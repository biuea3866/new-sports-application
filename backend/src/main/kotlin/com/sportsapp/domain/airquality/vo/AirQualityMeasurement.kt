package com.sportsapp.domain.airquality.vo

import java.time.ZonedDateTime

/**
 * 측정소 원시 측정값 (TDD.md §인터페이스 시그니처). [AirQualityGateway]가 3단계 체인 결과를 담아 반환한다.
 */
data class AirQualityMeasurement(
    val pm10: Int?,
    val pm25: Int?,
    val stationName: String?,
    val measuredAt: ZonedDateTime?,
) {
    companion object {
        /** 3단계 체인이 실패·타임아웃일 때 반환하는 빈 측정값(예외 전파 금지 계약). */
        fun empty(): AirQualityMeasurement = AirQualityMeasurement(
            pm10 = null,
            pm25 = null,
            stationName = null,
            measuredAt = null,
        )
    }
}
