package com.sportsapp.domain.booking.service

import com.sportsapp.domain.booking.dto.FacilitySchedule
import com.sportsapp.domain.booking.dto.FacilitySlotGenerationOutcome
import com.sportsapp.domain.booking.gateway.FacilityScheduleGateway
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 시설 운영시간·휴무 기준 향후 N일 예약 가능 슬롯을 멱등하게 생성한다(BE-58).
 *
 * 기존 (facility, date, timeRange) 조합은 skip하고 신규 날짜분만 생성해 V14 UNIQUE 제약과
 * 충돌 없이 재실행 가능하다. 슬롯 생성 자체는 [SlotDomainService.createSlot]에 위임해
 * 시설 소유권 검증·팩토리 규칙을 그대로 재사용한다.
 */
@Service
class SlotGenerationDomainService(
    private val facilityScheduleGateway: FacilityScheduleGateway,
    private val slotDomainService: SlotDomainService,
) {
    private val log = LoggerFactory.getLogger(SlotGenerationDomainService::class.java)

    fun generateAll(windowDays: Int): List<FacilitySlotGenerationOutcome> =
        facilityScheduleGateway.findSchedulableFacilities().map { generateSafely(it, windowDays) }

    fun generate(schedule: FacilitySchedule, windowDays: Int): Int {
        val zone = ZonedDateTime.now().zone
        val today = LocalDate.now(zone)
        val existingKeys = existingSlotKeysFor(schedule.facilityId)
        return (0 until windowDays)
            .map { today.plusDays(it.toLong()) }
            .sumOf { date -> generateForDate(schedule, date, zone, existingKeys) }
    }

    private fun existingSlotKeysFor(facilityId: String): Set<Pair<LocalDate, String>> =
        slotDomainService.listSlots(facilityId, null)
            .map { it.date.toLocalDate() to it.timeRange }
            .toSet()

    private fun generateForDate(
        schedule: FacilitySchedule,
        date: LocalDate,
        zone: ZoneId,
        existingKeys: Set<Pair<LocalDate, String>>,
    ): Int {
        if (schedule.holidays.contains(date)) return 0
        val weeklyHours = schedule.weeklyHours.firstOrNull { it.dayOfWeek == date.dayOfWeek } ?: return 0
        val newTimeRanges = weeklyHours.timeRanges.filterNot { (date to it) in existingKeys }
        newTimeRanges.forEach { timeRange -> createSlot(schedule, date, zone, timeRange, weeklyHours.capacity) }
        return newTimeRanges.size
    }

    private fun createSlot(schedule: FacilitySchedule, date: LocalDate, zone: ZoneId, timeRange: String, capacity: Int) {
        slotDomainService.createSlot(
            ownerId = schedule.ownerId,
            facilityId = schedule.facilityId,
            date = date.atStartOfDay(zone),
            timeRange = timeRange,
            capacity = capacity,
        )
    }

    private fun generateSafely(schedule: FacilitySchedule, windowDays: Int): FacilitySlotGenerationOutcome =
        runCatching { generate(schedule, windowDays) }
            .fold(
                onSuccess = { FacilitySlotGenerationOutcome(schedule.facilityId, it, succeeded = true) },
                onFailure = { ex ->
                    log.error("event=slot-generation-failed facilityId={} message={}", schedule.facilityId, ex.message, ex)
                    FacilitySlotGenerationOutcome(schedule.facilityId, 0, succeeded = false)
                },
            )
}
