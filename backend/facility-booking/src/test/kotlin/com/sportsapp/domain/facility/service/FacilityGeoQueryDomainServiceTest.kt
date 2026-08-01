package com.sportsapp.domain.facility.service

import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.repository.FacilityRepository
import com.sportsapp.domain.facility.vo.FacilityRegion
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.geo.Point

class FacilityGeoQueryDomainServiceTest : BehaviorSpec({

    Given("좌표·반경이 주어졌을 때") {
        val facilityRepository = mockk<FacilityRepository>()
        val service = FacilityGeoQueryDomainService(facilityRepository)
        val nearbyFacility = Facility(
            id = "FAC-NEAR",
            code = "CODE-NEAR",
            name = "근처 풋살장",
            gu = "강남구",
            type = "풋살장",
            address = "서울시 강남구",
            location = Point(127.0, 37.5),
            parking = true,
            tel = "02-0000-0000",
            homePage = "",
            eduYn = false,
            meta = emptyMap(),
            ownerUserId = null,
            sidoCode = FacilityRegion.UNSPECIFIED.sidoCode,
            sidoName = FacilityRegion.UNSPECIFIED.sidoName,
            sigunguCode = FacilityRegion.UNSPECIFIED.sigunguCode,
            sigunguName = FacilityRegion.UNSPECIFIED.sigunguName,
        )
        every { facilityRepository.findNear(37.5, 127.0, 1000.0) } returns listOf(nearbyFacility)

        When("findNear를 호출하면") {
            val result = service.findNear(37.5, 127.0, 1000.0)

            Then("repository 조회 결과를 그대로 반환한다") {
                result shouldContainExactly listOf(nearbyFacility)
            }
        }
    }
})
