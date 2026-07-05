package com.sportsapp.application.airquality.usecase

import com.sportsapp.domain.airquality.service.AirQualityDomainService
import com.sportsapp.domain.airquality.vo.AirQuality
import org.springframework.stereotype.Service

/**
 * 좌표 기준 실시간 대기질 조회 (BE-08, TDD.md §인터페이스 시그니처).
 * 외부 조회 전용이라 트랜잭션이 필요 없다.
 */
@Service
class GetAirQualityUseCase(
    private val airQualityDomainService: AirQualityDomainService,
) {

    fun execute(lat: Double, lng: Double): AirQuality =
        airQualityDomainService.current(lat, lng)
}
