package com.sportsapp.domain.recruitment

import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.exception.InvalidRecruitmentException
import com.sportsapp.domain.recruitment.exception.NotRecruiterException
import com.sportsapp.domain.recruitment.vo.RecruitmentStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.ZonedDateTime

class RecruitmentTest : BehaviorSpec({

    fun createRecruitment(
        capacity: Int = 5,
        feeAmount: BigDecimal = BigDecimal.ZERO,
        applicationDeadline: ZonedDateTime = ZonedDateTime.now().plusDays(10),
        recruiterUserId: Long = 1L,
    ): Recruitment = Recruitment.create(
        capacity = capacity,
        feeAmount = feeAmount,
        activityAt = applicationDeadline.plusDays(1),
        applicationDeadline = applicationDeadline,
        communityId = null,
        recruiterUserId = recruiterUserId,
    )

    Given("정원 여유가 있고 마감 전인 OPEN 상태의 모집") {
        val recruitment = createRecruitment(capacity = 5)

        Then("canApply()는 true를 반환한다") {
            recruitment.canApply(currentApplicantCount = 2) shouldBe true
        }
    }

    Given("마감이 지난 OPEN 상태의 모집") {
        val recruitment = createRecruitment(applicationDeadline = ZonedDateTime.now().minusDays(1))

        Then("canApply()는 false를 반환한다") {
            recruitment.canApply(currentApplicantCount = 0) shouldBe false
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
})
