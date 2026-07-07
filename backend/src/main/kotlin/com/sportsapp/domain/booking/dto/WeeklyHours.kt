package com.sportsapp.domain.booking.dto

import java.time.DayOfWeek

/**
 * booking 도메인이 소유하는 요일별 슬롯 생성 스케줄(BE-58).
 *
 * facility의 OperatingHours를 booking 관점으로 변환한 표현이며, 브레이크타임은 이미 제외된
 * "HH:mm-HH:mm" 형식의 timeRange 목록만 담는다. booking은 facility 도메인을 import하지 않는다.
 */
data class WeeklyHours(
    val dayOfWeek: DayOfWeek,
    val timeRanges: List<String>,
    val capacity: Int,
)
