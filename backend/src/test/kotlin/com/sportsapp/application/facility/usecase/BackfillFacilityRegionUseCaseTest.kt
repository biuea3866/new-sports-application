package com.sportsapp.application.facility.usecase

import com.sportsapp.domain.facility.dto.BackfillResult
import com.sportsapp.domain.facility.exception.FacilityRegionBackfillInProgressException
import com.sportsapp.domain.facility.service.FacilityRegionBackfillService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class BackfillFacilityRegionUseCaseTest : BehaviorSpec({

    Given("DomainService가 정상적으로 백필을 수행하는 상태") {
        val facilityRegionBackfillService = mockk<FacilityRegionBackfillService>()
        val useCase = BackfillFacilityRegionUseCase(facilityRegionBackfillService)
        val expected = BackfillResult(updated = 10, unspecified = 2)
        every { facilityRegionBackfillService.backfill(50) } returns expected

        When("execute를 호출하면") {
            val result = useCase.execute(50)

            Then("DomainService에 위임하고 결과를 그대로 반환한다") {
                result shouldBe expected
                verify(exactly = 1) { facilityRegionBackfillService.backfill(50) }
            }
        }
    }

    Given("DomainService가 락 획득 실패 예외를 던지는 상태") {
        val facilityRegionBackfillService = mockk<FacilityRegionBackfillService>()
        val useCase = BackfillFacilityRegionUseCase(facilityRegionBackfillService)
        every { facilityRegionBackfillService.backfill(any()) } throws FacilityRegionBackfillInProgressException()

        When("execute를 호출하면") {
            Then("예외를 그대로 전파한다") {
                shouldThrow<FacilityRegionBackfillInProgressException> {
                    useCase.execute(50)
                }
            }
        }
    }
})
