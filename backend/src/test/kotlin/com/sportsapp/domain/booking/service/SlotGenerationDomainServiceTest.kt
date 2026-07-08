package com.sportsapp.domain.booking.service

import com.sportsapp.domain.booking.dto.FacilitySchedule
import com.sportsapp.domain.booking.dto.WeeklyHours
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.gateway.FacilityScheduleGateway
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime

private fun dummySlot(facilityId: String, timeRange: String): Slot = Slot.create(
    facilityId = facilityId,
    date = ZonedDateTime.now(),
    timeRange = timeRange,
    capacity = 5,
    ownerId = 1L,
)

private fun everyDayWeeklyHours(timeRange: String = "09:00-10:00", capacity: Int = 5): List<WeeklyHours> =
    DayOfWeek.values().map { WeeklyHours(dayOfWeek = it, timeRanges = listOf(timeRange), capacity = capacity) }

class SlotGenerationDomainServiceTest : BehaviorSpec({

    Given("시설이 매일 09:00-10:00에 운영되고 기존 슬롯이 없을 때") {
        val slotDomainService = mockk<SlotDomainService>()
        val facilityScheduleGateway = mockk<FacilityScheduleGateway>()
        val service = SlotGenerationDomainService(facilityScheduleGateway, slotDomainService)
        val schedule = FacilitySchedule(
            facilityId = "FAC-01",
            ownerId = 1L,
            weeklyHours = everyDayWeeklyHours(),
            holidays = emptySet(),
        )
        every { slotDomainService.listSlots("FAC-01", null) } returns emptyList()
        every {
            slotDomainService.createSlot(any(), any(), any(), any(), any())
        } returns dummySlot("FAC-01", "09:00-10:00")

        When("향후 14일 윈도우로 슬롯을 생성하면") {
            val createdCount = service.generate(schedule, windowDays = 14)

            Then("14일치 슬롯이 모두 생성된다") {
                createdCount shouldBe 14
                verify(exactly = 14) { slotDomainService.createSlot(any(), any(), any(), any(), any()) }
            }
        }
    }

    Given("14일 윈도우에 해당하는 슬롯이 이미 모두 생성돼 있을 때") {
        val today = LocalDate.now()
        val zone = ZonedDateTime.now().zone
        val slotDomainService = mockk<SlotDomainService>()
        val facilityScheduleGateway = mockk<FacilityScheduleGateway>()
        val service = SlotGenerationDomainService(facilityScheduleGateway, slotDomainService)
        val schedule = FacilitySchedule(
            facilityId = "FAC-02",
            ownerId = 1L,
            weeklyHours = everyDayWeeklyHours(),
            holidays = emptySet(),
        )
        val existingSlots = (0 until 14).map { offset ->
            Slot.create(
                facilityId = "FAC-02",
                date = today.plusDays(offset.toLong()).atStartOfDay(zone),
                timeRange = "09:00-10:00",
                capacity = 5,
                ownerId = 1L,
            )
        }
        every { slotDomainService.listSlots("FAC-02", null) } returns existingSlots

        When("같은 14일 윈도우로 재실행하면") {
            val createdCount = service.generate(schedule, windowDays = 14)

            Then("멱등하게 스킵되어 신규 생성이 없다") {
                createdCount shouldBe 0
                verify(exactly = 0) { slotDomainService.createSlot(any(), any(), any(), any(), any()) }
            }
        }
    }

    Given("특정 날짜가 휴무일로 지정돼 있을 때") {
        val today = LocalDate.now()
        val holidayDate = today.plusDays(3)
        val slotDomainService = mockk<SlotDomainService>()
        val facilityScheduleGateway = mockk<FacilityScheduleGateway>()
        val service = SlotGenerationDomainService(facilityScheduleGateway, slotDomainService)
        val schedule = FacilitySchedule(
            facilityId = "FAC-03",
            ownerId = 1L,
            weeklyHours = everyDayWeeklyHours(),
            holidays = setOf(holidayDate),
        )
        every { slotDomainService.listSlots("FAC-03", null) } returns emptyList()
        every {
            slotDomainService.createSlot(any(), any(), any(), any(), any())
        } returns dummySlot("FAC-03", "09:00-10:00")

        When("14일 윈도우로 슬롯을 생성하면") {
            val createdCount = service.generate(schedule, windowDays = 14)

            Then("휴무일을 제외한 13일치만 생성된다") {
                createdCount shouldBe 13
            }
        }
    }

    Given("이전 실행으로 13일치 슬롯이 이미 존재하고 새 날짜 1일이 윈도우에 편입됐을 때") {
        val today = LocalDate.now()
        val zone = ZonedDateTime.now().zone
        val slotDomainService = mockk<SlotDomainService>()
        val facilityScheduleGateway = mockk<FacilityScheduleGateway>()
        val service = SlotGenerationDomainService(facilityScheduleGateway, slotDomainService)
        val schedule = FacilitySchedule(
            facilityId = "FAC-04",
            ownerId = 1L,
            weeklyHours = everyDayWeeklyHours(),
            holidays = emptySet(),
        )
        val existingSlots = (0 until 13).map { offset ->
            Slot.create(
                facilityId = "FAC-04",
                date = today.plusDays(offset.toLong()).atStartOfDay(zone),
                timeRange = "09:00-10:00",
                capacity = 5,
                ownerId = 1L,
            )
        }
        every { slotDomainService.listSlots("FAC-04", null) } returns existingSlots
        every {
            slotDomainService.createSlot(any(), any(), any(), any(), any())
        } returns dummySlot("FAC-04", "09:00-10:00")

        When("14일 윈도우로 슬롯을 생성하면") {
            val createdCount = service.generate(schedule, windowDays = 14)

            Then("새로 편입된 14일째 날짜분만 신규 생성된다") {
                createdCount shouldBe 1
                verify(exactly = 1) {
                    slotDomainService.createSlot(
                        ownerId = 1L,
                        facilityId = "FAC-04",
                        date = today.plusDays(13).atStartOfDay(zone),
                        timeRange = "09:00-10:00",
                        capacity = 5,
                    )
                }
            }
        }
    }

    Given("두 시설 중 한 시설의 슬롯 생성이 실패할 때") {
        val slotDomainService = mockk<SlotDomainService>()
        val facilityScheduleGateway = mockk<FacilityScheduleGateway>()
        val service = SlotGenerationDomainService(facilityScheduleGateway, slotDomainService)
        val scheduleA = FacilitySchedule(
            facilityId = "FAC-FAIL",
            ownerId = 1L,
            weeklyHours = everyDayWeeklyHours(),
            holidays = emptySet(),
        )
        val scheduleB = FacilitySchedule(
            facilityId = "FAC-OK",
            ownerId = 2L,
            weeklyHours = everyDayWeeklyHours(),
            holidays = emptySet(),
        )
        every { facilityScheduleGateway.findSchedulableFacilities() } returns listOf(scheduleA, scheduleB)
        every { slotDomainService.listSlots("FAC-FAIL", null) } returns emptyList()
        every { slotDomainService.listSlots("FAC-OK", null) } returns emptyList()
        every {
            slotDomainService.createSlot(
                ownerId = 1L,
                facilityId = "FAC-FAIL",
                date = any(),
                timeRange = "09:00-10:00",
                capacity = 5,
            )
        } throws RuntimeException("db error")
        every {
            slotDomainService.createSlot(
                ownerId = 2L,
                facilityId = "FAC-OK",
                date = any(),
                timeRange = "09:00-10:00",
                capacity = 5,
            )
        } returns dummySlot("FAC-OK", "09:00-10:00")

        When("전체 시설에 대해 슬롯을 생성하면") {
            val outcomes = service.generateAll(windowDays = 14)

            Then("실패한 시설은 격리되고 다른 시설은 정상 생성된다") {
                val failedOutcome = outcomes.first { it.facilityId == "FAC-FAIL" }
                val okOutcome = outcomes.first { it.facilityId == "FAC-OK" }
                failedOutcome.succeeded shouldBe false
                failedOutcome.createdCount shouldBe 0
                okOutcome.succeeded shouldBe true
                okOutcome.createdCount shouldBe 14
            }
        }
    }
})
