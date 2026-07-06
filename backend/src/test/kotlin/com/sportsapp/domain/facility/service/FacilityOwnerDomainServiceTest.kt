package com.sportsapp.domain.facility.service

import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.exception.FacilityHasActiveSlotException
import com.sportsapp.domain.facility.exception.FacilityNotFoundException
import com.sportsapp.domain.facility.exception.UnauthorizedFacilityAccessException
import com.sportsapp.domain.facility.gateway.GeocodingGateway
import com.sportsapp.domain.facility.gateway.RegionResolveGateway
import com.sportsapp.domain.facility.gateway.SlotQueryGateway
import com.sportsapp.domain.facility.repository.FacilityRepository
import com.sportsapp.domain.facility.vo.Coordinate
import com.sportsapp.domain.facility.vo.FacilityAttributes
import com.sportsapp.domain.facility.vo.FacilityRegion
import com.sportsapp.domain.facility.vo.OperatingHours
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class FacilityOwnerDomainServiceTest : BehaviorSpec({

    val facilityRepository = mockk<FacilityRepository>()
    val geocodingGateway = mockk<GeocodingGateway>()
    val slotQueryGateway = mockk<SlotQueryGateway>()
    val regionResolveGateway = mockk<RegionResolveGateway>()
    val service = FacilityOwnerDomainService(facilityRepository, geocodingGateway, slotQueryGateway, regionResolveGateway)

    fun attributes(lat: Double, lng: Double, address: String = "서울시 강남구", sidoHint: String? = null) = FacilityAttributes(
        code = "GN-001",
        name = "강남 풋살장",
        gu = "강남구",
        type = "풋살장",
        address = address,
        lat = lat,
        lng = lng,
        parking = true,
        tel = "02-0000-0000",
        homePage = "",
        eduYn = false,
        meta = emptyMap(),
        sidoHint = sidoHint,
    )

    every { facilityRepository.save(any()) } answers { firstArg() }

    Given("좌표가 입력된 경우") {
        every { regionResolveGateway.resolve(any(), any()) } returns FacilityRegion.UNSPECIFIED

        When("등록하면") {
            val facility = service.registerForOwner(attributes(lat = 37.5, lng = 127.0), ownerUserId = 1L)

            Then("geocoding 을 호출하지 않고 입력 좌표를 사용한다") {
                facility.lat shouldBe 37.5
                facility.lng shouldBe 127.0
                verify(exactly = 0) { geocodingGateway.geocode(any()) }
            }
        }
    }

    Given("좌표가 비어 있고(0,0) 주소가 있는 경우") {
        every { geocodingGateway.geocode("서울시 강남구") } returns Coordinate(lat = 37.4979, lng = 127.0276)
        every { regionResolveGateway.resolve(any(), any()) } returns FacilityRegion.UNSPECIFIED

        When("등록하면") {
            val facility = service.registerForOwner(attributes(lat = 0.0, lng = 0.0), ownerUserId = 1L)

            Then("geocoding 결과 좌표로 보강된다") {
                facility.lat shouldBe 37.4979
                facility.lng shouldBe 127.0276
            }
        }
    }

    Given("좌표가 비어 있고 geocoding 이 실패하는 경우") {
        every { geocodingGateway.geocode(any()) } returns null
        every { regionResolveGateway.resolve(any(), any()) } returns FacilityRegion.UNSPECIFIED

        When("등록하면") {
            val facility = service.registerForOwner(attributes(lat = 0.0, lng = 0.0), ownerUserId = 1L)

            Then("원본 좌표(0,0)를 유지한다") {
                facility.lat shouldBe 0.0
                facility.lng shouldBe 0.0
            }
        }
    }

    Given("owner가 sido 미입력으로 시설을 등록할 때") {
        val busan = FacilityRegion.of(sidoCode = "26", sidoName = "부산광역시", sigunguCode = "26350", sigunguName = "해운대구")
        every { regionResolveGateway.resolve("부산광역시 해운대구", null) } returns busan

        When("등록하면") {
            val facility = service.registerForOwner(
                attributes(lat = 37.5, lng = 127.0, address = "부산광역시 해운대구", sidoHint = null),
                ownerUserId = 1L,
            )

            Then("주소 파싱으로 해석된 시/도 코드가 자동 보간된다") {
                facility.sidoCode shouldBe "26"
                facility.sigunguCode shouldBe "26350"
            }
        }
    }

    Given("owner가 sido를 명시하여 시설을 등록할 때") {
        val busan = FacilityRegion.of(sidoCode = "26", sidoName = "부산광역시", sigunguCode = "26110", sigunguName = "중구")
        every { regionResolveGateway.resolve("서울시 중구", "부산") } returns busan

        When("등록하면") {
            val facility = service.registerForOwner(
                attributes(lat = 37.5, lng = 127.0, address = "서울시 중구", sidoHint = "부산"),
                ownerUserId = 1L,
            )

            Then("명시한 sido 값이 주소 파싱보다 우선 적용된다") {
                facility.sidoCode shouldBe "26"
                facility.sigunguCode shouldBe "26110"
            }
        }
    }

    Given("주소·sido 모두 지역 해석에 실패할 때") {
        val localFacilityRepository = mockk<FacilityRepository>()
        val localRegionResolveGateway = mockk<RegionResolveGateway>()
        val localService = FacilityOwnerDomainService(localFacilityRepository, geocodingGateway, slotQueryGateway, localRegionResolveGateway)
        every { localRegionResolveGateway.resolve("존재하지않는 우주정거장", null) } returns FacilityRegion.UNSPECIFIED
        every { localFacilityRepository.save(any()) } answers { firstArg() }

        When("등록하면") {
            val facility = localService.registerForOwner(
                attributes(lat = 37.5, lng = 127.0, address = "존재하지않는 우주정거장", sidoHint = null),
                ownerUserId = 1L,
            )

            Then("UNSPECIFIED로 보존되어 저장된다(스킵 없음)") {
                facility.sidoCode shouldBe FacilityRegion.UNSPECIFIED.sidoCode
                facility.sigunguCode shouldBe FacilityRegion.UNSPECIFIED.sigunguCode
                verify(exactly = 1) { localFacilityRepository.save(any()) }
            }
        }
    }

    Given("소유 시설에 활성 슬롯이 없는 경우") {
        val localFacilityRepository = mockk<FacilityRepository>()
        val localSlotQueryGateway = mockk<SlotQueryGateway>()
        val localService = FacilityOwnerDomainService(localFacilityRepository, geocodingGateway, localSlotQueryGateway, regionResolveGateway)
        val facility = Facility.create(attributes(lat = 37.5, lng = 127.0)).also { it.assignOwner(1L) }
        every { localFacilityRepository.findByIdAndOwnerUserId("f-001", 1L) } returns facility
        every { localSlotQueryGateway.hasActiveSlots("f-001") } returns false
        every { localFacilityRepository.save(any()) } answers { firstArg() }

        When("시설을 삭제하면") {
            localService.deleteForOwner("f-001", ownerUserId = 1L)

            Then("활성 슬롯을 확인한 뒤 삭제(soft-delete 저장)한다") {
                verify(exactly = 1) { localSlotQueryGateway.hasActiveSlots("f-001") }
                verify(exactly = 1) { localFacilityRepository.save(any()) }
            }
        }
    }

    Given("소유 시설에 활성 슬롯이 있는 경우") {
        val localFacilityRepository = mockk<FacilityRepository>()
        val localSlotQueryGateway = mockk<SlotQueryGateway>()
        val localService = FacilityOwnerDomainService(localFacilityRepository, geocodingGateway, localSlotQueryGateway, regionResolveGateway)
        val facility = Facility.create(attributes(lat = 37.5, lng = 127.0)).also { it.assignOwner(1L) }
        every { localFacilityRepository.findByIdAndOwnerUserId("f-002", 1L) } returns facility
        every { localSlotQueryGateway.hasActiveSlots("f-002") } returns true

        When("시설을 삭제하면") {
            Then("FacilityHasActiveSlotException을 던지고 삭제하지 않는다") {
                shouldThrow<FacilityHasActiveSlotException> {
                    localService.deleteForOwner("f-002", ownerUserId = 1L)
                }
                verify(exactly = 0) { localFacilityRepository.save(any()) }
            }
        }
    }

    Given("owner가 meta만 수정하고 sido를 지정하지 않을 때") {
        val localFacilityRepository = mockk<FacilityRepository>()
        val localRegionResolveGateway = mockk<RegionResolveGateway>()
        val localService = FacilityOwnerDomainService(localFacilityRepository, geocodingGateway, mockk(), localRegionResolveGateway)
        val facility = Facility.create(attributes(lat = 37.5, lng = 127.0)).also { it.assignOwner(1L) }
        every { localFacilityRepository.findByIdAndOwnerUserId("f-003", 1L) } returns facility
        every { localFacilityRepository.save(any()) } answers { firstArg() }

        When("meta를 수정하면") {
            val updated = localService.updateMetaForOwner("f-003", 1L, mapOf("key" to "value"))

            Then("region 재해석 없이 기존 UNSPECIFIED 지역이 유지된다") {
                updated.meta["key"] shouldBe "value"
                updated.sidoCode shouldBe FacilityRegion.UNSPECIFIED.sidoCode
                verify(exactly = 0) { localRegionResolveGateway.resolve(any(), any()) }
            }
        }
    }

    Given("owner가 시설 수정 시 sido를 함께 전달할 때") {
        val localFacilityRepository = mockk<FacilityRepository>()
        val localRegionResolveGateway = mockk<RegionResolveGateway>()
        val localService = FacilityOwnerDomainService(localFacilityRepository, geocodingGateway, mockk(), localRegionResolveGateway)
        val facility = Facility.create(attributes(lat = 37.5, lng = 127.0, address = "서울시 강남구")).also { it.assignOwner(1L) }
        every { localFacilityRepository.findByIdAndOwnerUserId("f-004", 1L) } returns facility
        every { localFacilityRepository.save(any()) } answers { firstArg() }
        val busan = FacilityRegion.of(sidoCode = "26", sidoName = "부산광역시", sigunguCode = "26350", sigunguName = "해운대구")
        every { localRegionResolveGateway.resolve("서울시 강남구", "부산") } returns busan

        When("meta와 sido를 함께 수정하면") {
            val updated = localService.updateMetaForOwner("f-004", 1L, mapOf("key" to "value"), sido = "부산")

            Then("재해석된 지역이 반영된다") {
                updated.sidoCode shouldBe "26"
                updated.sigunguCode shouldBe "26350"
                updated.meta["key"] shouldBe "value"
            }
        }
    }

    Given("소유 시설에 운영시간을 등록할 때") {
        val localFacilityRepository = mockk<FacilityRepository>()
        val localService = FacilityOwnerDomainService(localFacilityRepository, geocodingGateway, slotQueryGateway, regionResolveGateway)
        val facility = Facility.create(attributes(lat = 37.5, lng = 127.0)).also { it.assignOwner(1L) }
        val hours = listOf(
            OperatingHours(
                dayOfWeek = DayOfWeek.MONDAY,
                openTime = LocalTime.of(6, 0),
                closeTime = LocalTime.of(22, 0),
                capacity = 10,
            ),
        )
        every { localFacilityRepository.findById("f-010") } returns facility
        every { localFacilityRepository.save(any()) } answers { firstArg() }

        When("소유자가 registerOperatingHours를 호출하면") {
            val updated = localService.registerOperatingHours("f-010", 1L, hours)

            Then("Facility에 운영시간이 반영되어 저장된다") {
                updated.operatingHours shouldHaveSize 1
                verify(exactly = 1) { localFacilityRepository.save(any()) }
            }
        }
    }

    Given("소유하지 않은 시설에 운영시간을 등록하려 할 때") {
        val localFacilityRepository = mockk<FacilityRepository>()
        val localService = FacilityOwnerDomainService(localFacilityRepository, geocodingGateway, slotQueryGateway, regionResolveGateway)
        val facility = Facility.create(attributes(lat = 37.5, lng = 127.0)).also { it.assignOwner(1L) }
        val hours = listOf(
            OperatingHours(
                dayOfWeek = DayOfWeek.MONDAY,
                openTime = LocalTime.of(6, 0),
                closeTime = LocalTime.of(22, 0),
                capacity = 10,
            ),
        )
        every { localFacilityRepository.findById("f-010") } returns facility

        When("소유자가 아닌 사용자가 registerOperatingHours를 호출하면") {
            Then("UnauthorizedFacilityAccessException을 던지고 저장하지 않는다") {
                shouldThrow<UnauthorizedFacilityAccessException> {
                    localService.registerOperatingHours("f-010", 99L, hours)
                }
                verify(exactly = 0) { localFacilityRepository.save(any()) }
            }
        }
    }

    Given("존재하지 않는 시설에 운영시간을 등록할 때") {
        val localFacilityRepository = mockk<FacilityRepository>()
        val localService = FacilityOwnerDomainService(localFacilityRepository, geocodingGateway, slotQueryGateway, regionResolveGateway)
        every { localFacilityRepository.findById("f-999") } returns null

        When("registerOperatingHours를 호출하면") {
            Then("FacilityNotFoundException을 던진다") {
                shouldThrow<FacilityNotFoundException> {
                    localService.registerOperatingHours("f-999", 1L, emptyList())
                }
            }
        }
    }

    Given("소유 시설에 휴무일을 추가할 때") {
        val localFacilityRepository = mockk<FacilityRepository>()
        val localService = FacilityOwnerDomainService(localFacilityRepository, geocodingGateway, slotQueryGateway, regionResolveGateway)
        val facility = Facility.create(attributes(lat = 37.5, lng = 127.0)).also { it.assignOwner(1L) }
        val date = LocalDate.of(2026, 7, 6)
        every { localFacilityRepository.findById("f-011") } returns facility
        every { localFacilityRepository.save(any()) } answers { firstArg() }

        When("소유자가 addHoliday를 호출하면") {
            val updated = localService.addHoliday("f-011", 1L, date)

            Then("Facility에 휴무일이 추가되어 저장된다") {
                updated.isHoliday(date) shouldBe true
                verify(exactly = 1) { localFacilityRepository.save(any()) }
            }
        }
    }

    Given("소유하지 않은 시설에 휴무일을 추가하려 할 때") {
        val localFacilityRepository = mockk<FacilityRepository>()
        val localService = FacilityOwnerDomainService(localFacilityRepository, geocodingGateway, slotQueryGateway, regionResolveGateway)
        val facility = Facility.create(attributes(lat = 37.5, lng = 127.0)).also { it.assignOwner(1L) }
        val date = LocalDate.of(2026, 7, 6)
        every { localFacilityRepository.findById("f-011") } returns facility

        When("소유자가 아닌 사용자가 addHoliday를 호출하면") {
            Then("UnauthorizedFacilityAccessException을 던지고 저장하지 않는다") {
                shouldThrow<UnauthorizedFacilityAccessException> {
                    localService.addHoliday("f-011", 99L, date)
                }
                verify(exactly = 0) { localFacilityRepository.save(any()) }
            }
        }
    }

    Given("휴무일이 등록된 소유 시설에서 휴무일을 제거할 때") {
        val localFacilityRepository = mockk<FacilityRepository>()
        val localService = FacilityOwnerDomainService(localFacilityRepository, geocodingGateway, slotQueryGateway, regionResolveGateway)
        val date = LocalDate.of(2026, 7, 6)
        val facility = Facility.create(attributes(lat = 37.5, lng = 127.0)).also {
            it.assignOwner(1L)
            it.addHoliday(date)
        }
        every { localFacilityRepository.findById("f-012") } returns facility
        every { localFacilityRepository.save(any()) } answers { firstArg() }

        When("소유자가 removeHoliday를 호출하면") {
            val updated = localService.removeHoliday("f-012", 1L, date)

            Then("Facility에서 휴무일이 제거되어 저장된다") {
                updated.isHoliday(date) shouldBe false
                verify(exactly = 1) { localFacilityRepository.save(any()) }
            }
        }
    }
})
