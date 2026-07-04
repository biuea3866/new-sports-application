package com.sportsapp.domain.airquality.service

import com.sportsapp.domain.airquality.gateway.AirQualityGateway
import com.sportsapp.domain.airquality.vo.AirQuality
import org.springframework.stereotype.Service

/**
 * 좌표 기준 실시간 대기질 조회 오케스트레이션 (TDD.md §인터페이스 시그니처).
 * gateway가 반환한 [AirQualityMeasurement][com.sportsapp.domain.airquality.vo.AirQualityMeasurement]를
 * 등급이 조립된 [AirQuality]로 변환한다.
 */
@Service
class AirQualityDomainService(
    private val airQualityGateway: AirQualityGateway,
) {

    /** [lat],[lng] 기준 실시간 대기질을 조회한다. gateway가 empty를 반환하면 representativeGrade=UNKNOWN인 AirQuality가 된다. */
    fun current(lat: Double, lng: Double): AirQuality =
        AirQuality.of(airQualityGateway.current(lat, lng))
}
