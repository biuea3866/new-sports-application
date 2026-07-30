package com.sportsapp.domain.booking.dto

import java.time.LocalDate

/**
 * 자동 슬롯 생성 대상 시설의 스케줄(BE-58).
 *
 * booking 도메인이 소유하는 DTO로, facility 도메인을 import하지 않는다.
 * [com.sportsapp.domain.booking.gateway.FacilityScheduleGateway] 구현체가 facility의 운영시간·휴무를
 * 이 타입으로 변환해 전달한다.
 */
data class FacilitySchedule(
    val facilityId: String,
    val ownerId: Long,
    val weeklyHours: List<WeeklyHours>,
    val holidays: Set<LocalDate>,
)
