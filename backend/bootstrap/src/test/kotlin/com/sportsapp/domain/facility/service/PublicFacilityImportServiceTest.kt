package com.sportsapp.domain.facility.service

import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.gateway.PublicFacility
import com.sportsapp.domain.facility.gateway.PublicSportsFacilityGateway
import com.sportsapp.domain.facility.gateway.RegionResolveGateway
import com.sportsapp.domain.facility.repository.FacilityRepository
import com.sportsapp.domain.facility.vo.FacilityRegion
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

class PublicFacilityImportServiceTest : BehaviorSpec({

    fun busanFacility(externalId: String = "PUB-001") = PublicFacility(
        externalId = externalId,
        name = "해운대 체육관",
        gu = "해운대구",
        type = "체육관",
        address = "부산광역시 해운대구 재송동 111",
        lat = 35.1,
        lng = 129.1,
        tel = "051-000-0000",
    )

    Given("data.go.kr 대량 적재 시 부산 주소 시설이 포함되어 있을 때") {
        val publicSportsFacilityGateway = mockk<PublicSportsFacilityGateway>()
        val facilityRepository = mockk<FacilityRepository>()
        val regionResolveGateway = mockk<RegionResolveGateway>()
        val service = PublicFacilityImportService(publicSportsFacilityGateway, facilityRepository, regionResolveGateway)

        val busan = FacilityRegion.of(sidoCode = "26", sidoName = "부산광역시", sigunguCode = "26350", sigunguName = "해운대구")
        every { publicSportsFacilityGateway.fetchPage(1, 10) } returns listOf(busanFacility())
        every { publicSportsFacilityGateway.fetchPage(2, 10) } returns emptyList()
        every { regionResolveGateway.resolve("부산광역시 해운대구 재송동 111", null) } returns busan
        every { facilityRepository.findByCode("PUB-001") } returns null
        val savedFacility = slot<Facility>()
        every { facilityRepository.save(capture(savedFacility)) } answers { firstArg() }

        When("importAll을 호출하면") {
            val result = service.importAll(maxPages = 2, numOfRows = 10)

            Then("부산 코드로 매핑되어 저장된다") {
                result.insertedCount shouldBe 1
                savedFacility.captured.sidoCode shouldBe "26"
                savedFacility.captured.sigunguCode shouldBe "26350"
            }
        }
    }

    Given("data.go.kr 응답에 시/도 매핑이 실패하는 시설이 포함되어 있을 때") {
        val publicSportsFacilityGateway = mockk<PublicSportsFacilityGateway>()
        val facilityRepository = mockk<FacilityRepository>()
        val regionResolveGateway = mockk<RegionResolveGateway>()
        val service = PublicFacilityImportService(publicSportsFacilityGateway, facilityRepository, regionResolveGateway)

        every { publicSportsFacilityGateway.fetchPage(1, 10) } returns listOf(busanFacility(externalId = "PUB-002").copy(address = "알 수 없는 주소"))
        every { publicSportsFacilityGateway.fetchPage(2, 10) } returns emptyList()
        every { regionResolveGateway.resolve("알 수 없는 주소", null) } returns FacilityRegion.UNSPECIFIED
        every { facilityRepository.findByCode("PUB-002") } returns null
        val savedFacility = slot<Facility>()
        every { facilityRepository.save(capture(savedFacility)) } answers { firstArg() }

        When("importAll을 호출하면") {
            val result = service.importAll(maxPages = 2, numOfRows = 10)

            Then("UNSPECIFIED로 저장된다") {
                result.insertedCount shouldBe 1
                savedFacility.captured.sidoCode shouldBe FacilityRegion.UNSPECIFIED.sidoCode
                verify(exactly = 0) { facilityRepository.upsertByCode(any()) }
            }
        }
    }
})
