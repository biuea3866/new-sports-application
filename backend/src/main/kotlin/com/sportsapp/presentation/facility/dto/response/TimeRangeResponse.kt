package com.sportsapp.presentation.facility.dto.response

import com.sportsapp.domain.facility.vo.TimeRange

data class TimeRangeResponse(
    val start: String,
    val end: String,
) {
    companion object {
        fun of(timeRange: TimeRange): TimeRangeResponse = TimeRangeResponse(
            start = timeRange.start.toString(),
            end = timeRange.end.toString(),
        )
    }
}
