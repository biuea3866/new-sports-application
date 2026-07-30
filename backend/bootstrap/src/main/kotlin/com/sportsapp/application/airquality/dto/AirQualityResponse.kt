package com.sportsapp.application.airquality.dto

import com.sportsapp.domain.airquality.vo.AirQuality
import java.time.format.DateTimeFormatter

data class AirQualityResponse(
    val pm10: Int?,
    val pm25: Int?,
    val pm10Grade: String,
    val pm25Grade: String,
    val representativeGrade: String,
    val stationName: String?,
    val measuredAt: String?,
) {
    companion object {
        /** [AirQuality]를 그대로 응답으로 노출한다 — empty(등급 UNKNOWN·값 null)도 그대로 노출한다(FE가 해석). */
        fun of(airQuality: AirQuality): AirQualityResponse = AirQualityResponse(
            pm10 = airQuality.pm10,
            pm25 = airQuality.pm25,
            pm10Grade = airQuality.pm10Grade.name,
            pm25Grade = airQuality.pm25Grade.name,
            representativeGrade = airQuality.representativeGrade.name,
            stationName = airQuality.stationName,
            measuredAt = airQuality.measuredAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        )
    }
}
