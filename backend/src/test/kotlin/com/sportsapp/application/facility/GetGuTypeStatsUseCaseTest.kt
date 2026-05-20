package com.sportsapp.application.facility

import com.sportsapp.domain.facility.FacilityDomainService
import com.sportsapp.domain.facility.GuTypeCount
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class GetGuTypeStatsUseCaseTest : BehaviorSpec({

    val facilityDomainService = mockk<FacilityDomainService>()
    val getGuTypeStatsUseCase = GetGuTypeStatsUseCase(facilityDomainService)

    Given("MongoDB aggregation 결과가 3개 그룹인 상태") {
        val aggregationResult = listOf(
            GuTypeCount(gu = "강남구", type = "수영장", count = 5),
            GuTypeCount(gu = "강남구", type = "풋살장", count = 3),
            GuTypeCount(gu = "서초구", type = "헬스장", count = 2),
        )
        every { facilityDomainService.aggregateGuType() } returns aggregationResult

        When("[U-02] execute를 호출하면") {
            val result = getGuTypeStatsUseCase.execute()

            Then("aggregation 결과를 GuTypeCount DTO 리스트로 변환하여 반환한다") {
                result shouldHaveSize 3
                result[0].gu shouldBe "강남구"
                result[0].type shouldBe "수영장"
                result[0].count shouldBe 5L
                result[1].gu shouldBe "강남구"
                result[1].type shouldBe "풋살장"
                result[1].count shouldBe 3L
                result[2].gu shouldBe "서초구"
                result[2].type shouldBe "헬스장"
                result[2].count shouldBe 2L
            }
        }
    }

    Given("aggregation 결과가 빈 상태") {
        every { facilityDomainService.aggregateGuType() } returns emptyList()

        When("execute를 호출하면") {
            val result = getGuTypeStatsUseCase.execute()

            Then("빈 리스트를 반환한다") {
                result shouldHaveSize 0
            }
        }
    }
})
