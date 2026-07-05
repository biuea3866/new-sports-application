package com.sportsapp.application.facility.usecase

import com.sportsapp.domain.facility.dto.RegionTypeCount
import com.sportsapp.domain.facility.service.FacilityDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class GetRegionTypeStatsUseCaseTest : BehaviorSpec({

    val facilityDomainService = mockk<FacilityDomainService>()
    val getRegionTypeStatsUseCase = GetRegionTypeStatsUseCase(facilityDomainService)

    Given("시도·시군구·유형별 집계 결과가 2개 그룹인 상태") {
        val aggregationResult = listOf(
            RegionTypeCount(sidoCode = "26", sidoName = "부산광역시", sigunguCode = "26410", sigunguName = "해운대구", type = "수영장", count = 5),
            RegionTypeCount(sidoCode = "11", sidoName = "서울특별시", sigunguCode = "11680", sigunguName = "강남구", type = "헬스장", count = 2),
        )
        every { facilityDomainService.aggregateRegionType() } returns aggregationResult

        When("execute를 호출하면") {
            val result = getRegionTypeStatsUseCase.execute()

            Then("집계 결과를 그대로 반환한다") {
                result shouldHaveSize 2
                result[0].sidoName shouldBe "부산광역시"
                result[1].sigunguName shouldBe "강남구"
            }
        }
    }

    Given("집계 결과가 빈 상태") {
        every { facilityDomainService.aggregateRegionType() } returns emptyList()

        When("execute를 호출하면") {
            val result = getRegionTypeStatsUseCase.execute()

            Then("빈 리스트를 반환한다") {
                result shouldHaveSize 0
            }
        }
    }
})
