package com.sportsapp.domain.recruitment

import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.entity.RecruitmentStatus
import com.sportsapp.domain.recruitment.exception.InvalidRecruitmentException
import com.sportsapp.domain.recruitment.exception.NotRecruiterException
import com.sportsapp.domain.recruitment.exception.RecruitmentApplicationClosedException
import com.sportsapp.domain.recruitment.exception.RecruitmentFullException
import com.sportsapp.domain.recruitment.exception.RecruitmentNotOpenException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.ZonedDateTime

class RecruitmentTest : BehaviorSpec({

    fun createRecruitment(
        title: String = "주말 축구 모임",
        description: String? = null,
        capacity: Int = 5,
        feeAmount: BigDecimal = BigDecimal.ZERO,
        applicationDeadline: ZonedDateTime = ZonedDateTime.now().plusDays(10),
        recruiterUserId: Long = 1L,
    ): Recruitment = Recruitment.create(
        title = title,
        description = description,
        capacity = capacity,
        feeAmount = feeAmount,
        activityAt = applicationDeadline.plusDays(1),
        applicationDeadline = applicationDeadline,
        communityId = null,
        recruiterUserId = recruiterUserId,
    )

    Given("정원 여유가 있고 마감 전인 OPEN 상태의 모집") {
        val recruitment = createRecruitment(capacity = 5)

        Then("requireApplicable()은 예외 없이 통과한다") {
            recruitment.requireApplicable(currentApplicantCount = 2)
        }
    }

    Given("마감이 지난 OPEN 상태의 모집") {
        val recruitment = createRecruitment(applicationDeadline = ZonedDateTime.now().minusDays(1))

        Then("requireApplicable()은 RecruitmentApplicationClosedException을 던진다") {
            shouldThrow<RecruitmentApplicationClosedException> {
                recruitment.requireApplicable(currentApplicantCount = 0)
            }
        }
    }

    Given("CANCELLED 상태의 모집") {
        val recruitment = createRecruitment(recruiterUserId = 1L).apply { cancelByHost(userId = 1L) }

        Then("requireApplicable()은 RecruitmentNotOpenException을 던진다") {
            shouldThrow<RecruitmentNotOpenException> {
                recruitment.requireApplicable(currentApplicantCount = 0)
            }
        }
    }

    Given("정원이 가득 차 CLOSED로 전이된 모집") {
        val recruitment = createRecruitment(capacity = 1).apply { closeWhenFull(currentApplicantCount = 1) }

        Then("requireApplicable()은 RecruitmentNotOpenException을 던진다") {
            shouldThrow<RecruitmentNotOpenException> {
                recruitment.requireApplicable(currentApplicantCount = 1)
            }
        }
    }

    Given("정원이 3명이고 이미 3명이 신청한 OPEN 상태의 모집") {
        val recruitment = createRecruitment(capacity = 3)

        Then("requireApplicable()은 RecruitmentFullException을 던진다") {
            shouldThrow<RecruitmentFullException> {
                recruitment.requireApplicable(currentApplicantCount = 3)
            }
        }
    }

    Given("정원이 3명인 모집에 현재 신청자가 정원과 같아진 경우") {
        val recruitment = createRecruitment(capacity = 3)

        When("closeWhenFull()을 호출하면") {
            recruitment.closeWhenFull(currentApplicantCount = 3)

            Then("상태가 CLOSED로 전이된다") {
                recruitment.status shouldBe RecruitmentStatus.CLOSED
            }
        }
    }

    Given("정원이 3명인 모집에 현재 신청자가 정원보다 적은 경우") {
        val recruitment = createRecruitment(capacity = 3)

        When("closeWhenFull()을 호출하면") {
            recruitment.closeWhenFull(currentApplicantCount = 1)

            Then("상태는 OPEN을 유지한다") {
                recruitment.status shouldBe RecruitmentStatus.OPEN
            }
        }
    }

    Given("개설자가 recruiterUserId=1L인 모집") {
        val recruitment = createRecruitment(recruiterUserId = 1L)

        When("개설자 본인이 cancelByHost를 호출하면") {
            recruitment.cancelByHost(userId = 1L)

            Then("상태가 CANCELLED로 전이된다") {
                recruitment.status shouldBe RecruitmentStatus.CANCELLED
            }
        }

        When("개설자가 아닌 사용자가 cancelByHost를 호출하면") {
            Then("NotRecruiterException을 던진다") {
                shouldThrow<NotRecruiterException> {
                    recruitment.cancelByHost(userId = 99L)
                }
            }
        }
    }

    Given("capacity가 0인 모집 생성 요청") {
        Then("InvalidRecruitmentException을 던진다") {
            shouldThrow<InvalidRecruitmentException> {
                createRecruitment(capacity = 0)
            }
        }
    }

    Given("feeAmount가 음수인 모집 생성 요청") {
        Then("InvalidRecruitmentException을 던진다") {
            shouldThrow<InvalidRecruitmentException> {
                createRecruitment(feeAmount = BigDecimal(-1))
            }
        }
    }

    Given("feeAmount가 0인 모집 생성 요청") {
        Then("정상적으로 생성된다") {
            val recruitment = createRecruitment(feeAmount = BigDecimal.ZERO)
            recruitment.feeAmount.compareTo(BigDecimal.ZERO) shouldBe 0
        }
    }

    Given("feeAmount가 0원인 모집") {
        val recruitment = createRecruitment(feeAmount = BigDecimal.ZERO)

        Then("isFree()는 true를 반환한다") {
            recruitment.isFree() shouldBe true
        }
    }

    Given("feeAmount가 0보다 큰 모집") {
        val recruitment = createRecruitment(feeAmount = BigDecimal("10000"))

        Then("isFree()는 false를 반환한다") {
            recruitment.isFree() shouldBe false
        }
    }

    Given("title이 빈 문자열인 모집 생성 요청") {
        Then("InvalidRecruitmentException을 던진다") {
            shouldThrow<InvalidRecruitmentException> {
                createRecruitment(title = "")
            }
        }
    }

    Given("title이 공백 문자로만 이루어진 모집 생성 요청") {
        Then("InvalidRecruitmentException을 던진다") {
            shouldThrow<InvalidRecruitmentException> {
                createRecruitment(title = "   ")
            }
        }
    }

    Given("title이 200자를 초과하는 모집 생성 요청") {
        Then("InvalidRecruitmentException을 던진다") {
            shouldThrow<InvalidRecruitmentException> {
                createRecruitment(title = "가".repeat(201))
            }
        }
    }

    Given("title이 정확히 200자인 모집 생성 요청") {
        Then("정상적으로 생성된다") {
            val recruitment = createRecruitment(title = "가".repeat(200))
            recruitment.title.length shouldBe 200
        }
    }

    Given("description 없이 모집을 생성하는 요청") {
        Then("description은 null로 생성된다") {
            val recruitment = createRecruitment(description = null)
            recruitment.description shouldBe null
        }
    }

    Given("description을 포함한 모집 생성 요청") {
        Then("description이 그대로 저장된다") {
            val recruitment = createRecruitment(description = "매주 토요일 오전 축구 모임입니다")
            recruitment.description shouldBe "매주 토요일 오전 축구 모임입니다"
        }
    }

    Given("정원이 가득 차 CLOSED로 전이된 모집에서 만료로 정원 여유가 생겼을 때 (W1-11d 만료 스위퍼 — 정원 복원)") {
        val recruitment = createRecruitment(capacity = 3).apply { closeWhenFull(currentApplicantCount = 3) }

        When("reopenIfBelowCapacity(현재 활성 신청 2건)를 호출하면") {
            recruitment.reopenIfBelowCapacity(currentApplicantCount = 2)

            Then("OPEN으로 재전이되어 신청이 다시 가능해진다") {
                recruitment.status shouldBe RecruitmentStatus.OPEN
            }
        }
    }

    Given("정원이 가득 차 CLOSED로 전이된 모집에서 정원 여유가 없을 때 (경계값)") {
        val recruitment = createRecruitment(capacity = 3).apply { closeWhenFull(currentApplicantCount = 3) }

        When("reopenIfBelowCapacity(현재 활성 신청도 정원과 동일한 3건)를 호출하면") {
            recruitment.reopenIfBelowCapacity(currentApplicantCount = 3)

            Then("CLOSED를 유지한다") {
                recruitment.status shouldBe RecruitmentStatus.CLOSED
            }
        }
    }

    Given("OPEN 상태의 모집") {
        val recruitment = createRecruitment(capacity = 3)

        When("reopenIfBelowCapacity를 호출하면") {
            recruitment.reopenIfBelowCapacity(currentApplicantCount = 0)

            Then("OPEN을 그대로 유지한다 (원래 CLOSED가 아니었으므로 재전이 대상이 아니다)") {
                recruitment.status shouldBe RecruitmentStatus.OPEN
            }
        }
    }

    Given("개설자가 취소한 CANCELLED 상태의 모집") {
        val recruitment = createRecruitment(capacity = 3, recruiterUserId = 1L).apply {
            closeWhenFull(currentApplicantCount = 3)
            cancelByHost(userId = 1L)
        }

        When("reopenIfBelowCapacity를 호출하면") {
            recruitment.reopenIfBelowCapacity(currentApplicantCount = 0)

            Then("CANCELLED를 그대로 유지한다 (취소된 모집을 되살리지 않는다)") {
                recruitment.status shouldBe RecruitmentStatus.CANCELLED
            }
        }
    }
})
