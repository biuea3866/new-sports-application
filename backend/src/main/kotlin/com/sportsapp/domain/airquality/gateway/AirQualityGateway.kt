package com.sportsapp.domain.airquality.gateway

import com.sportsapp.domain.airquality.vo.AirQualityMeasurement

/**
 * 좌표→실시간 측정 계약 (TDD.md §인터페이스 시그니처). 구현체는 infrastructure의
 * 3단계 API 체인(`AirKoreaAirQualityGatewayImpl`)이 담당한다.
 */
interface AirQualityGateway {

    /** [lat],[lng] 기준 실시간 측정값을 조회한다. 3단계 실패·타임아웃 시 [AirQualityMeasurement.empty]를 반환한다(예외 전파 금지). */
    fun current(lat: Double, lng: Double): AirQualityMeasurement
}
