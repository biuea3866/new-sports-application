package com.sportsapp.application.recruitment

import com.sportsapp.application.recruitment.usecase.ListRecruitmentApplicationsForOrderHistoryUseCase
import com.sportsapp.domain.recruitment.dto.ApplicationWithRecruitmentTitle
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * 통합 주문내역(BE-08)이 fan-out 하는 recruitment 신청 이력 원격 공급 UseCase (S2-05).
 */
class ListRecruitmentApplicationsForOrderHistoryUseCaseTest : BehaviorSpec({

    val createdAt = ZonedDateTime.of(2026, 6, 2, 9, 0, 0, 0, ZoneOffset.UTC)

    Given("본인 신청 이력이 있는 사용자") {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val useCase = ListRecruitmentApplicationsForOrderHistoryUseCase(recruitmentDomainService)
        val application = ApplicationWithRecruitmentTitle(
            applicationId = 11L,
            status = ApplicationStatus.CONFIRMED,
            recruitmentTitle = "주말 축구 모임",
            paymentId = 701L,
            createdAt = createdAt,
            feeAmount = BigDecimal("15000"),
        )
        every { recruitmentDomainService.listApplicationsWithTitleBy(9L) } returns listOf(application)

        When("execute(applicantUserId=9)를 호출하면") {
            val result = useCase.execute(9L)

            Then("계약 필드만 담은 응답을 반환한다") {
                result.size shouldBe 1
                result[0].sourceId shouldBe 11L
                result[0].title shouldBe "주말 축구 모임"
                result[0].status shouldBe ApplicationStatus.CONFIRMED
                result[0].paymentId shouldBe 701L
                result[0].createdAt shouldBe createdAt
            }

            Then("결제 금액을 함께 공급한다 — edge 가 payment 를 역참조하지 않고 주문내역에 금액을 노출하는 근거다") {
                result[0].amount shouldBe BigDecimal("15000")
            }
        }
    }

    Given("참가비 0원(무료) 모임에 신청한 사용자") {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val useCase = ListRecruitmentApplicationsForOrderHistoryUseCase(recruitmentDomainService)
        val application = ApplicationWithRecruitmentTitle(
            applicationId = 12L,
            status = ApplicationStatus.CONFIRMED,
            recruitmentTitle = "무료 러닝 크루",
            paymentId = null,
            createdAt = createdAt,
            feeAmount = BigDecimal.ZERO,
        )
        every { recruitmentDomainService.listApplicationsWithTitleBy(9L) } returns listOf(application)

        When("execute(applicantUserId=9)를 호출하면") {
            val result = useCase.execute(9L)

            Then("무료 확정값 0을 그대로 공급한다 (금액 확정 불가인 null 과 구분한다)") {
                result[0].amount shouldBe BigDecimal.ZERO
            }
        }
    }

    Given("참조 모집이 소실돼 금액을 확정할 수 없는 신청") {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val useCase = ListRecruitmentApplicationsForOrderHistoryUseCase(recruitmentDomainService)
        val application = ApplicationWithRecruitmentTitle(
            applicationId = 13L,
            status = ApplicationStatus.CONFIRMED,
            recruitmentTitle = "삭제된 모임",
            paymentId = null,
            createdAt = createdAt,
            feeAmount = null,
        )
        every { recruitmentDomainService.listApplicationsWithTitleBy(9L) } returns listOf(application)

        When("execute(applicantUserId=9)를 호출하면") {
            val result = useCase.execute(9L)

            Then("금액을 null 로 공급한다 — 무료 확정값 0 과 구분되어야 한다 (0 으로 방어하지 않는다)") {
                result[0].amount shouldBe null
            }
        }
    }

    Given("신청 이력이 없는 사용자") {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val useCase = ListRecruitmentApplicationsForOrderHistoryUseCase(recruitmentDomainService)
        every { recruitmentDomainService.listApplicationsWithTitleBy(999L) } returns emptyList()

        When("execute(applicantUserId=999)를 호출하면") {
            val result = useCase.execute(999L)

            Then("빈 목록을 정상 반환한다") {
                result.shouldBeEmpty()
            }
        }
    }
})
