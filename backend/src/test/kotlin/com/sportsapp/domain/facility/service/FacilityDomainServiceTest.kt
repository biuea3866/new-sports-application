package com.sportsapp.domain.facility.service

import com.sportsapp.domain.facility.dto.LegacyRow
import com.sportsapp.domain.facility.dto.RegionTypeCount
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.gateway.RegionResolveGateway
import com.sportsapp.domain.facility.repository.FacilityRepository
import com.sportsapp.domain.facility.vo.FacilityAttributes
import com.sportsapp.domain.facility.vo.FacilityRegion
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class FacilityDomainServiceTest : BehaviorSpec({

    fun buildAttributes(
        code: String = "GN-001",
        address: String = "서울시 강남구",
        sidoHint: String? = null,
    ) = FacilityAttributes(
        code = code,
        name = "테스트 시설",
        gu = "강남구",
        type = "수영장",
        address = address,
        lat = 37.5,
        lng = 127.0,
        parking = true,
        tel = "02-0000-0000",
        homePage = "",
        eduYn = false,
        meta = emptyMap(),
        sidoHint = sidoHint,
    )

    fun buildLegacyRow(legacyId: String, ycode: String = "37.5", xcode: String = "127.0") = LegacyRow(
        legacyId = legacyId,
        name = "시설 $legacyId",
        gu = "강남구",
        type = "수영장",
        address = "서울시 강남구",
        ycode = ycode,
        xcode = xcode,
        parking = true,
        tel = "02-0000-0000",
        homePage = "",
        eduYn = false,
        extraFields = emptyMap(),
    )

    Given("sidoCode·sigunguCode 필터가 주어졌을 때") {
        val facilityRepository = mockk<FacilityRepository>()
        val regionResolveGateway = mockk<RegionResolveGateway>()
        val service = FacilityDomainService(facilityRepository, regionResolveGateway)
        val pageable = PageRequest.of(0, 50)
        every { facilityRepository.findAll("26", "26410", null, null, pageable) } returns PageImpl(emptyList())

        When("list를 호출하면") {
            service.list("26", "26410", null, null, pageable)

            Then("repository.findAll에 5개 인자가 그대로 위임된다") {
                verify(exactly = 1) { facilityRepository.findAll("26", "26410", null, null, pageable) }
            }
        }
    }

    Given("region 집계 요청이 주어졌을 때") {
        val facilityRepository = mockk<FacilityRepository>()
        val regionResolveGateway = mockk<RegionResolveGateway>()
        val service = FacilityDomainService(facilityRepository, regionResolveGateway)
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

    Given("주소 해석이 가능한 시설을 등록할 때") {
        val facilityRepository = mockk<FacilityRepository>()
        val regionResolveGateway = mockk<RegionResolveGateway>()
        val service = FacilityDomainService(facilityRepository, regionResolveGateway)
        val resolved = FacilityRegion.of("11", "서울특별시", "11680", "강남구")
        every { regionResolveGateway.resolve("서울시 강남구", null) } returns resolved
        every { facilityRepository.save(any()) } answers { firstArg() }

        When("register를 호출하면") {
            val facility = service.register(buildAttributes())

            Then("해석된 region이 저장 전 반영된다") {
                facility.sidoCode shouldBe "11"
                facility.sidoName shouldBe "서울특별시"
                facility.sigunguCode shouldBe "11680"
                facility.sigunguName shouldBe "강남구"
                verify(exactly = 1) { regionResolveGateway.resolve("서울시 강남구", null) }
            }
        }
    }

    Given("주소 해석이 실패해 UNSPECIFIED가 반환될 때") {
        val facilityRepository = mockk<FacilityRepository>()
        val regionResolveGateway = mockk<RegionResolveGateway>()
        val service = FacilityDomainService(facilityRepository, regionResolveGateway)
        every { regionResolveGateway.resolve(any(), any()) } returns FacilityRegion.UNSPECIFIED
        every { facilityRepository.save(any()) } answers { firstArg() }

        When("register를 호출하면") {
            val facility = service.register(buildAttributes(address = "알 수 없는 주소"))

            Then("UNSPECIFIED region이 보존된 채 저장된다") {
                facility.sidoCode shouldBe FacilityRegion.UNSPECIFIED.sidoCode
                facility.sigunguCode shouldBe FacilityRegion.UNSPECIFIED.sigunguCode
            }
        }
    }

    Given("2건의 유효한 레거시 행이 주어졌을 때") {
        val facilityRepository = mockk<FacilityRepository>()
        val regionResolveGateway = mockk<RegionResolveGateway>()
        val service = FacilityDomainService(facilityRepository, regionResolveGateway)
        val resolved = FacilityRegion.of("26", "부산광역시", "26410", "해운대구")
        val savedFacilities = mutableListOf<Facility>()
        every { regionResolveGateway.resolve(any(), any()) } returns resolved
        every { facilityRepository.findByCode(any()) } returns null
        every { facilityRepository.save(capture(savedFacilities)) } answers { firstArg() }

        When("bulkImport를 호출하면") {
            val rows = listOf(buildLegacyRow("A-001"), buildLegacyRow("A-002"))
            service.bulkImport(rows)

            Then("행마다 region 해석이 호출되고 저장된 시설에 반영된다") {
                verify(exactly = 2) { regionResolveGateway.resolve("서울시 강남구", null) }
                savedFacilities shouldHaveSize 2
                savedFacilities.all { it.sidoCode == "26" } shouldBe true
            }
        }
    }

    Given("좌표 변환이 실패하는 행이 섞인 레거시 데이터가 주어졌을 때") {
        val facilityRepository = mockk<FacilityRepository>()
        val regionResolveGateway = mockk<RegionResolveGateway>()
        val service = FacilityDomainService(facilityRepository, regionResolveGateway)
        every { regionResolveGateway.resolve(any(), any()) } returns FacilityRegion.UNSPECIFIED
        every { facilityRepository.findByCode(any()) } returns null
        every { facilityRepository.save(any()) } answers { firstArg() }

        When("bulkImport를 호출하면") {
            val rows = listOf(buildLegacyRow("VALID-001"), buildLegacyRow("INVALID-001", ycode = "NOT_A_NUMBER"))
            val result = service.bulkImport(rows)

            Then("좌표 변환 실패 행은 region 해석 없이 스킵된다") {
                result.skippedCount shouldBe 1
                verify(exactly = 1) { regionResolveGateway.resolve(any(), any()) }
            }
        }
    }
})
