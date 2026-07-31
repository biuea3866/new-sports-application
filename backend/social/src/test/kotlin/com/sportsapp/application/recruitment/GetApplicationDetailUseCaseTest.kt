package com.sportsapp.application.recruitment

import com.sportsapp.application.recruitment.usecase.GetApplicationDetailUseCase
import com.sportsapp.domain.recruitment.dto.ApplicationDetail
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.exception.UnauthorizedApplicationAccessException
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime

class GetApplicationDetailUseCaseTest : BehaviorSpec({

    val recruitmentDomainService = mockk<RecruitmentDomainService>()
    val useCase = GetApplicationDetailUseCase(recruitmentDomainService)

    Given("본인 소유의 신청 상세 조회 요청") {
        val detail = ApplicationDetail(
            applicationId = 11L,
            recruitmentId = 1L,
            recruitmentTitle = "주말 축구 모임",
            status = ApplicationStatus.CONFIRMED,
            feeAmount = BigDecimal("10000"),
            paymentId = 701L,
            createdAt = ZonedDateTime.of(2026, 6, 2, 9, 0, 0, 0, ZoneOffset.UTC),
        )
        every { recruitmentDomainService.getApplicationDetailBy(applicationId = 11L, requesterUserId = 100L) } returns detail

        When("execute(applicationId=11, requesterUserId=100)를 호출하면") {
            val result = useCase.execute(applicationId = 11L, requesterUserId = 100L)

            Then("모집명·참가비를 포함한 상세 응답을 반환한다") {
                result.applicationId shouldBe 11L
                result.recruitmentId shouldBe 1L
                result.recruitmentTitle shouldBe "주말 축구 모임"
                result.status shouldBe ApplicationStatus.CONFIRMED
                result.feeAmount shouldBe BigDecimal("10000")
                result.paymentId shouldBe 701L
                result.createdAt shouldBe ZonedDateTime.of(2026, 6, 2, 9, 0, 0, 0, ZoneOffset.UTC)
            }
        }
    }

    Given("본인 소유가 아닌 신청 상세 조회 요청") {
        every {
            recruitmentDomainService.getApplicationDetailBy(applicationId = 11L, requesterUserId = 999L)
        } throws UnauthorizedApplicationAccessException(11L)

        When("execute(applicationId=11, requesterUserId=999)를 호출하면") {
            Then("UnauthorizedApplicationAccessException을 던진다") {
                shouldThrow<UnauthorizedApplicationAccessException> {
                    useCase.execute(applicationId = 11L, requesterUserId = 999L)
                }
            }
        }
    }
})
