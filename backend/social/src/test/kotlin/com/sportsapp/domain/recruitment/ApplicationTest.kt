package com.sportsapp.domain.recruitment

import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.event.ApplicationRefundRequestedEvent
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.exception.ApplicationCancellationClosedException
import com.sportsapp.domain.recruitment.exception.InvalidApplicationStateException
import com.sportsapp.domain.recruitment.exception.UnauthorizedApplicationAccessException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.ZonedDateTime

class ApplicationTest : BehaviorSpec({

    Given("신규 생성된 Application") {
        val application = Application.create(recruitmentId = 10L, applicantUserId = 1L)

        Then("status=PENDING, paymentId=null로 생성된다") {
            application.status shouldBe ApplicationStatus.PENDING
            application.paymentId.shouldBeNull()
        }
    }

    Given("PENDING 상태의 Application (confirm 대상)") {
        val application = Application.create(recruitmentId = 10L, applicantUserId = 1L)

        When("confirm(paymentId)를 호출하면") {
            application.confirm(paymentId = 100L)

            Then("CONFIRMED로 전이되고 paymentId가 채워진다") {
                application.status shouldBe ApplicationStatus.CONFIRMED
                application.paymentId shouldBe 100L
            }
        }
    }

    Given("이미 CONFIRMED된 Application") {
        val application = Application.create(recruitmentId = 10L, applicantUserId = 1L)
        application.confirm(paymentId = 100L)

        When("confirm을 다시 호출하면") {
            application.confirm(paymentId = 999L)

            Then("상태와 paymentId가 변경되지 않고 멱등하게 처리된다") {
                application.status shouldBe ApplicationStatus.CONFIRMED
                application.paymentId shouldBe 100L
            }
        }
    }

    Given("CANCELLED 상태의 Application (W1-11d 만료 스위퍼가 취소한 이후 결제 확정 이벤트가 뒤늦게 도달하는 상황)") {
        val application = Application.create(recruitmentId = 10L, applicantUserId = 1L)
        application.cancelPending()

        When("confirm(paymentId)을 호출하면") {
            Then("InvalidApplicationStateException을 던진다 (CANCELLED에서 CONFIRMED로 전이 불가)") {
                shouldThrow<InvalidApplicationStateException> {
                    application.confirm(paymentId = 999L)
                }
                application.status shouldBe ApplicationStatus.CANCELLED
            }
        }
    }

    Given("PENDING 상태의 Application (cancelPending 대상)") {
        val application = Application.create(recruitmentId = 10L, applicantUserId = 1L)

        When("cancelPending을 호출하면") {
            application.cancelPending()

            Then("CANCELLED로 전이된다") {
                application.status shouldBe ApplicationStatus.CANCELLED
            }
        }
    }

    Given("마감 전인 CONFIRMED 상태의 Application (feeAmount 10000원, 수수료율 5%)") {
        val application = Application.create(recruitmentId = 10L, applicantUserId = 1L)
        application.confirm(paymentId = 100L)
        val deadline = ZonedDateTime.now().plusDays(5)

        When("cancelByApplicant를 호출하면") {
            application.cancelByApplicant(applicationDeadline = deadline, refundAmount = BigDecimal("9500"))

            Then("CANCELLED로 전이된다") {
                application.status shouldBe ApplicationStatus.CANCELLED
            }

            Then("환불 요청 이벤트가 1건 적재된다") {
                val events = application.pullDomainEvents()
                events.size shouldBe 1
                val event = events[0] as ApplicationRefundRequestedEvent
                event.paymentId shouldBe 100L
                event.refundAmount.compareTo(BigDecimal("9500")) shouldBe 0
            }
        }
    }

    Given("마감이 지난 CONFIRMED 상태의 Application") {
        val application = Application.create(recruitmentId = 10L, applicantUserId = 1L)
        application.confirm(paymentId = 100L)
        val pastDeadline = ZonedDateTime.now().minusDays(1)

        When("cancelByApplicant를 호출하면") {
            Then("ApplicationCancellationClosedException을 던진다") {
                shouldThrow<ApplicationCancellationClosedException> {
                    application.cancelByApplicant(applicationDeadline = pastDeadline, refundAmount = BigDecimal.ZERO)
                }
            }
        }
    }

    Given("이미 CANCELLED된 Application") {
        val application = Application.create(recruitmentId = 10L, applicantUserId = 1L)
        application.confirm(paymentId = 100L)
        application.cancelByApplicant(applicationDeadline = ZonedDateTime.now().plusDays(5), refundAmount = BigDecimal("9500"))
        application.pullDomainEvents()

        When("cancelByApplicant를 다시 호출하면") {
            application.cancelByApplicant(applicationDeadline = ZonedDateTime.now().minusDays(1), refundAmount = BigDecimal("9500"))

            Then("상태 가드로 no-op이며 이벤트도 추가되지 않는다") {
                application.status shouldBe ApplicationStatus.CANCELLED
                application.pullDomainEvents().size shouldBe 0
            }
        }
    }

    Given("CANCELLED 상태의 Application (markRefunded 대상)") {
        val application = Application.create(recruitmentId = 10L, applicantUserId = 1L)
        application.confirm(paymentId = 100L)
        application.cancelByApplicant(applicationDeadline = ZonedDateTime.now().plusDays(5), refundAmount = BigDecimal("9500"))

        When("markRefunded를 호출하면") {
            application.markRefunded()

            Then("REFUNDED로 전이된다") {
                application.status shouldBe ApplicationStatus.REFUNDED
            }
        }
    }

    Given("이미 REFUNDED 상태의 Application") {
        val application = Application.create(recruitmentId = 10L, applicantUserId = 1L)
        application.confirm(paymentId = 100L)
        application.cancelByApplicant(applicationDeadline = ZonedDateTime.now().plusDays(5), refundAmount = BigDecimal("9500"))
        application.markRefunded()

        When("markRefunded를 다시 호출하면") {
            application.markRefunded()

            Then("상태는 REFUNDED로 유지되고 예외가 발생하지 않는다") {
                application.status shouldBe ApplicationStatus.REFUNDED
            }
        }
    }

    Given("PENDING 상태의 무료(참가비 0원) Application") {
        val application = Application.create(recruitmentId = 10L, applicantUserId = 1L)

        When("confirmFree를 호출하면") {
            application.confirmFree()

            Then("paymentId 없이 CONFIRMED로 전이된다") {
                application.status shouldBe ApplicationStatus.CONFIRMED
                application.paymentId.shouldBeNull()
            }
        }
    }

    Given("이미 CONFIRMED된 Application (confirmFree 재호출 대상)") {
        val application = Application.create(recruitmentId = 10L, applicantUserId = 1L)
        application.confirm(paymentId = 100L)

        When("confirmFree를 다시 호출하면") {
            application.confirmFree()

            Then("상태와 paymentId가 변경되지 않고 멱등하게 처리된다") {
                application.status shouldBe ApplicationStatus.CONFIRMED
                application.paymentId shouldBe 100L
            }
        }
    }

    Given("applicantUserId=1L인 Application") {
        val application = Application.create(recruitmentId = 10L, applicantUserId = 1L)

        When("본인이 requireOwnedBy를 호출하면") {
            Then("예외가 발생하지 않는다") {
                application.requireOwnedBy(1L)
            }
        }

        When("타인이 requireOwnedBy를 호출하면") {
            Then("UnauthorizedApplicationAccessException을 던진다") {
                shouldThrow<UnauthorizedApplicationAccessException> {
                    application.requireOwnedBy(99L)
                }
            }
        }
    }

    Given("CONFIRMED 상태의 Application (개설자 모집 취소로 인한 전액환불 대상)") {
        val application = Application.create(recruitmentId = 10L, applicantUserId = 1L)
        application.confirm(paymentId = 100L)

        When("cancelForRecruitmentCancellation을 호출하면") {
            application.cancelForRecruitmentCancellation(refundAmount = BigDecimal("10000"))

            Then("CANCELLED로 전이되고 전액환불 이벤트가 적재된다") {
                application.status shouldBe ApplicationStatus.CANCELLED
                val events = application.pullDomainEvents()
                events.size shouldBe 1
                val event = events[0] as ApplicationRefundRequestedEvent
                event.paymentId shouldBe 100L
                event.refundAmount.compareTo(BigDecimal("10000")) shouldBe 0
                event.reason shouldBe "RECRUITMENT_CANCELLED"
            }
        }
    }

    Given("이미 CANCELLED된 Application (개설자 모집 취소가 중복 호출된 상황)") {
        val application = Application.create(recruitmentId = 10L, applicantUserId = 1L)
        application.confirm(paymentId = 100L)
        application.cancelForRecruitmentCancellation(refundAmount = BigDecimal("10000"))
        application.pullDomainEvents()

        When("cancelForRecruitmentCancellation을 다시 호출하면") {
            application.cancelForRecruitmentCancellation(refundAmount = BigDecimal("10000"))

            Then("상태 가드로 no-op이며 중복 환불 이벤트가 적재되지 않는다") {
                application.status shouldBe ApplicationStatus.CANCELLED
                application.pullDomainEvents().size shouldBe 0
            }
        }
    }
})
