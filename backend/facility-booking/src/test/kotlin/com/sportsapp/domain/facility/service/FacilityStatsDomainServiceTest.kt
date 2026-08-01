package com.sportsapp.domain.facility.service

import com.sportsapp.domain.facility.dto.RegionTypeCount
import com.sportsapp.domain.facility.repository.FacilityRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class FacilityStatsDomainServiceTest : BehaviorSpec({

    Given("region 집계 요청이 주어졌을 때") {
        val facilityRepository = mockk<FacilityRepository>()
        val service = FacilityStatsDomainService(facilityRepository)
        val aggregation = listOf(
            RegionTypeCount(sidoCode = "26", sidoName = "부산광역시", sigunguCode = "26410", sigunguName = "해운대구", type = "수영장", count = 3L),
        )
        every { facilityRepository.aggregateRegionType() } returns aggregation

        When("aggregateRegionType을 호출하면") {
            val result = service.aggregateRegionType()

            Then("repository 집계 결과를 그대로 반환한다") {
                result shouldHaveSize 1
                result[0].sidoName shouldBe "부산광역시"
            }
        }
    }
})
