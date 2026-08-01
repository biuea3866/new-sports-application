package com.sportsapp.domain.facility.service

import com.sportsapp.domain.facility.dto.LegacyRow
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.gateway.RegionResolveGateway
import com.sportsapp.domain.facility.repository.FacilityRepository
import com.sportsapp.domain.facility.vo.FacilityAttributes
import com.sportsapp.domain.facility.vo.FacilityRegion
import com.sportsapp.domain.facility.vo.OperatingHours
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.DayOfWeek
import java.time.LocalTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.geo.Point

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

    fun buildFacility(
        id: String,
        ownerUserId: Long? = null,
        operatingHours: List<OperatingHours> = emptyList(),
    ) = Facility(
        id = id,
        code = "CODE-$id",
        name = "시설 $id",
        gu = "강남구",
        type = "풋살장",
        address = "서울시 강남구",
        location = Point(127.0, 37.5),
        parking = true,
        tel = "02-0000-0000",
        homePage = "",
        eduYn = false,
        meta = emptyMap(),
        ownerUserId = ownerUserId,
        sidoCode = FacilityRegion.UNSPECIFIED.sidoCode,
        sidoName = FacilityRegion.UNSPECIFIED.sidoName,
        sigunguCode = FacilityRegion.UNSPECIFIED.sigunguCode,
        sigunguName = FacilityRegion.UNSPECIFIED.sigunguName,
        operatingHours = operatingHours,
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

    Given("id에 해당하는 시설이 존재할 때") {
        val facilityRepository = mockk<FacilityRepository>()
        val regionResolveGateway = mockk<RegionResolveGateway>()
        val service = FacilityDomainService(facilityRepository, regionResolveGateway)
        val facility = buildFacility(id = "FAC-01", ownerUserId = 1L)
        every { facilityRepository.findById("FAC-01") } returns facility

        When("findBy를 호출하면") {
            val result = service.findBy("FAC-01")

            Then("시설을 반환한다") {
                result shouldBe facility
            }
        }
    }

    Given("id에 해당하는 시설이 존재하지 않을 때") {
        val facilityRepository = mockk<FacilityRepository>()
        val regionResolveGateway = mockk<RegionResolveGateway>()
        val service = FacilityDomainService(facilityRepository, regionResolveGateway)
        every { facilityRepository.findById("FAC-MISSING") } returns null

        When("findBy를 호출하면") {
            val result = service.findBy("FAC-MISSING")

            Then("null을 반환한다 (예외 전파 금지)") {
                result shouldBe null
            }
        }
    }

    Given("운영시간·소유자를 가진 시설과 대상이 아닌 시설이 섞여 있을 때") {
        val facilityRepository = mockk<FacilityRepository>()
        val regionResolveGateway = mockk<RegionResolveGateway>()
        val service = FacilityDomainService(facilityRepository, regionResolveGateway)
        val schedulableHours = OperatingHours(
            dayOfWeek = DayOfWeek.MONDAY,
            openTime = LocalTime.of(9, 0),
            closeTime = LocalTime.of(12, 0),
            capacity = 4,
        )
        val schedulable = buildFacility(id = "FAC-SCHEDULABLE", ownerUserId = 10L, operatingHours = listOf(schedulableHours))
        val noHours = buildFacility(id = "FAC-NO-HOURS", ownerUserId = 20L)
        val noOwner = buildFacility(id = "FAC-NO-OWNER", ownerUserId = null, operatingHours = listOf(schedulableHours))
        val page: Page<Facility> = PageImpl(listOf(schedulable, noHours, noOwner))
        every { facilityRepository.findAllForBackfill(any()) } returns page

        When("findAllSchedulable을 호출하면") {
            val result = service.findAllSchedulable()

            Then("운영시간이 등록되고 소유자가 있는 시설만 반환된다") {
                result shouldContainExactlyInAnyOrder listOf(schedulable)
            }
        }
    }

    Given("스케줄 대상 시설이 하나도 없을 때") {
        val facilityRepository = mockk<FacilityRepository>()
        val regionResolveGateway = mockk<RegionResolveGateway>()
        val service = FacilityDomainService(facilityRepository, regionResolveGateway)
        every { facilityRepository.findAllForBackfill(any()) } returns PageImpl(emptyList())

        When("findAllSchedulable을 호출하면") {
            val result = service.findAllSchedulable()

            Then("빈 목록을 반환한다") {
                result.shouldBeEmpty()
            }
        }
    }

    Given("시설 수가 페이지 크기를 넘겨 여러 페이지로 나뉘어 있을 때") {
        val facilityRepository = mockk<FacilityRepository>()
        val regionResolveGateway = mockk<RegionResolveGateway>()
        val service = FacilityDomainService(facilityRepository, regionResolveGateway)
        val schedulableHours = OperatingHours(
            dayOfWeek = DayOfWeek.MONDAY,
            openTime = LocalTime.of(9, 0),
            closeTime = LocalTime.of(12, 0),
            capacity = 4,
        )
        val firstPageFacility = buildFacility(id = "FAC-PAGE-1", ownerUserId = 1L, operatingHours = listOf(schedulableHours))
        val secondPageFacility = buildFacility(id = "FAC-PAGE-2", ownerUserId = 2L, operatingHours = listOf(schedulableHours))
        val firstPage: Page<Facility> = PageImpl(listOf(firstPageFacility), PageRequest.of(0, 1), 2)
        val secondPage: Page<Facility> = PageImpl(listOf(secondPageFacility), PageRequest.of(1, 1), 2)
        every { facilityRepository.findAllForBackfill(match<Pageable> { it.pageNumber == 0 }) } returns firstPage
        every { facilityRepository.findAllForBackfill(match<Pageable> { it.pageNumber == 1 }) } returns secondPage

        When("findAllSchedulable을 호출하면") {
            val result = service.findAllSchedulable()

            Then("모든 페이지의 시설이 전량 수집된다") {
                result shouldContainExactlyInAnyOrder listOf(firstPageFacility, secondPageFacility)
            }
        }
    }
})
