package com.sportsapp.presentation.facility.dto.response

import com.sportsapp.domain.facility.vo.OperatingHours

data class OperatingHoursResponse(
    val dayOfWeek: String,
    val openTime: String,
    val closeTime: String,
    val breaks: List<TimeRangeResponse>,
    val slotDurationMinutes: Int,
    val capacity: Int,
) {
    companion object {
        fun of(operatingHours: OperatingHours): OperatingHoursResponse = OperatingHoursResponse(
            dayOfWeek = operatingHours.dayOfWeek.name,
            openTime = operatingHours.openTime.toString(),
            closeTime = operatingHours.closeTime.toString(),
            breaks = operatingHours.breaks.map { TimeRangeResponse.of(it) },
            slotDurationMinutes = operatingHours.slotDurationMinutes,
            capacity = operatingHours.capacity,
        )
    }
}
