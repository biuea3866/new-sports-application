package com.sportsapp.domain.facility.service

import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.exception.FacilityNotFoundException
import com.sportsapp.domain.facility.exception.UnauthorizedFacilityAccessException
import com.sportsapp.domain.facility.repository.FacilityRepository
import com.sportsapp.domain.facility.vo.FacilityAttributes
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

class FacilityScheduleOwnerDomainServiceTest : BehaviorSpec({

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

    Given("소유 시설에 운영시간을 등록할 때") {
        val localFacilityRepository = mockk<FacilityRepository>()
        val localService = FacilityScheduleOwnerDomainService(localFacilityRepository)
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
        val localService = FacilityScheduleOwnerDomainService(localFacilityRepository)
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
        val localService = FacilityScheduleOwnerDomainService(localFacilityRepository)
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
        val localService = FacilityScheduleOwnerDomainService(localFacilityRepository)
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
        val localService = FacilityScheduleOwnerDomainService(localFacilityRepository)
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
        val localService = FacilityScheduleOwnerDomainService(localFacilityRepository)
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
