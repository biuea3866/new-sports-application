package com.sportsapp.domain.facility.service

import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.exception.FacilityHasActiveSlotException
import com.sportsapp.domain.facility.gateway.GeocodingGateway
import com.sportsapp.domain.facility.gateway.SlotQueryGateway
import com.sportsapp.domain.facility.repository.FacilityRepository
import com.sportsapp.domain.facility.vo.Coordinate
import com.sportsapp.domain.facility.vo.FacilityAttributes
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class FacilityOwnerDomainServiceTest : BehaviorSpec({

    val facilityRepository = mockk<FacilityRepository>()
    val geocodingGateway = mockk<GeocodingGateway>()
    val slotQueryGateway = mockk<SlotQueryGateway>()
    val service = FacilityOwnerDomainService(facilityRepository, geocodingGateway, slotQueryGateway)

    fun attributes(lat: Double, lng: Double, address: String = "서울시 강남구") = FacilityAttributes(
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
    )

    every { facilityRepository.save(any()) } answers { firstArg() }

    Given("좌표가 입력된 경우") {
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

        When("등록하면") {
            val facility = service.registerForOwner(attributes(lat = 0.0, lng = 0.0), ownerUserId = 1L)

            Then("원본 좌표(0,0)를 유지한다") {
                facility.lat shouldBe 0.0
                facility.lng shouldBe 0.0
            }
        }
    }

    Given("소유 시설에 활성 슬롯이 없는 경우") {
        val localFacilityRepository = mockk<FacilityRepository>()
        val localSlotQueryGateway = mockk<SlotQueryGateway>()
        val localService = FacilityOwnerDomainService(localFacilityRepository, geocodingGateway, localSlotQueryGateway)
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
        val localService = FacilityOwnerDomainService(localFacilityRepository, geocodingGateway, localSlotQueryGateway)
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
})
