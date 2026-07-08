package com.sportsapp.domain.facility.vo

import com.sportsapp.domain.facility.exception.InvalidFacilityException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * 요일별 운영시간 값객체. 브레이크타임을 제외한 슬롯 단위 시간대 슬라이싱을 캡슐화한다.
 * BE-58(자동 슬롯 생성 스케줄러)이 [slotRangesFor]를 계약으로 읽어간다.
 */
data class OperatingHours(
    val dayOfWeek: DayOfWeek,
    val openTime: LocalTime,
    val closeTime: LocalTime,
    val breaks: List<TimeRange> = emptyList(),
    val slotDurationMinutes: Int = DEFAULT_SLOT_DURATION_MINUTES,
    val capacity: Int,
) {
    init {
        if (!openTime.isBefore(closeTime)) {
            throw InvalidFacilityException("openTime($openTime) must be before closeTime($closeTime)")
        }
        if (slotDurationMinutes <= 0) {
            throw InvalidFacilityException("slotDurationMinutes must be positive, got: $slotDurationMinutes")
        }
        if (capacity <= 0) {
            throw InvalidFacilityException("capacity must be positive, got: $capacity")
        }
    }

    // 지정 날짜의 요일이 이 운영시간의 요일과 다르면 빈 목록(에러 아님)을 반환한다.
    fun slotRangesFor(date: LocalDate): List<TimeRange> {
        if (date.dayOfWeek != dayOfWeek) return emptyList()
        return generateSlots().filterNot { slot -> breaks.any { it.overlaps(slot) } }
    }

    private fun generateSlots(): List<TimeRange> {
        val slots = mutableListOf<TimeRange>()
        var cursor = openTime
        while (true) {
            val slotEnd = cursor.plusMinutes(slotDurationMinutes.toLong())
            if (slotEnd.isAfter(closeTime)) break
            slots += TimeRange(cursor, slotEnd)
            cursor = slotEnd
        }
        return slots
    }

    companion object {
        const val DEFAULT_SLOT_DURATION_MINUTES = 60
    }
}
