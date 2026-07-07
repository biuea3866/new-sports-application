package com.sportsapp.domain.booking.gateway

import com.sportsapp.domain.booking.dto.FacilitySchedule

/**
 * 자동 슬롯 생성 대상 시설의 운영시간·휴무 스케줄을 조회하는 게이트웨이(BE-58).
 *
 * booking 도메인은 facility 도메인을 직접 import하지 않으며, 이 interface를 통해서만
 * 시설의 스케줄을 booking 소유 DTO([FacilitySchedule])로 전달받는다.
 * 구현체는 infrastructure 레이어에 위치한다.
 */
interface FacilityScheduleGateway {
    fun findSchedulableFacilities(): List<FacilitySchedule>
}
