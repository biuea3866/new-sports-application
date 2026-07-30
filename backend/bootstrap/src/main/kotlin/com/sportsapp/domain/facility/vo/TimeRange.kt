package com.sportsapp.domain.facility.vo

import com.sportsapp.domain.facility.exception.InvalidFacilityException
import java.time.LocalTime

/**
 * HH:mm-HH:mm 형태의 시각 구간 값객체. 시설 운영시간·브레이크타임·슬롯 슬라이스에 공통으로 쓰인다.
 */
data class TimeRange(
    val start: LocalTime,
    val end: LocalTime,
) {
    init {
        if (!start.isBefore(end)) {
            throw InvalidFacilityException("TimeRange start($start) must be before end($end)")
        }
    }

    fun overlaps(other: TimeRange): Boolean = start.isBefore(other.end) && other.start.isBefore(end)
}
