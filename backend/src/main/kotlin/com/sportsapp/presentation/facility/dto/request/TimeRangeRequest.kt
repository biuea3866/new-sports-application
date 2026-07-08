package com.sportsapp.presentation.facility.dto.request

import com.sportsapp.domain.facility.vo.TimeRange
import java.time.LocalTime

data class TimeRangeRequest(
    val start: String,
    val end: String,
) {
    fun toVo(): TimeRange = TimeRange(
        start = LocalTime.parse(start),
        end = LocalTime.parse(end),
    )
}
