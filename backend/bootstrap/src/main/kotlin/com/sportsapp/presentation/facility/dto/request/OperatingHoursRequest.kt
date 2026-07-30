package com.sportsapp.presentation.facility.dto.request

import com.sportsapp.domain.facility.vo.OperatingHours
import java.time.DayOfWeek
import java.time.LocalTime

data class OperatingHoursRequest(
    val dayOfWeek: String,
    val openTime: String,
    val closeTime: String,
    val breaks: List<TimeRangeRequest> = emptyList(),
    val slotDurationMinutes: Int = OperatingHours.DEFAULT_SLOT_DURATION_MINUTES,
    val capacity: Int,
) {
    fun toVo(): OperatingHours = OperatingHours(
        dayOfWeek = DayOfWeek.valueOf(dayOfWeek),
        openTime = LocalTime.parse(openTime),
        closeTime = LocalTime.parse(closeTime),
        breaks = breaks.map { it.toVo() },
        slotDurationMinutes = slotDurationMinutes,
        capacity = capacity,
    )
}
