package com.sportsapp.domain.airquality.vo

import java.time.ZonedDateTime

/**
 * 등급이 조립된 대기질 값 (TDD.md §인터페이스 시그니처). [AirQualityDomainService]가 [AirQualityMeasurement]로부터 조립한다.
 */
data class AirQuality(
    val pm10: Int?,
    val pm25: Int?,
    val pm10Grade: AirQualityGrade,
    val pm25Grade: AirQualityGrade,
    val representativeGrade: AirQualityGrade,
    val stationName: String?,
    val measuredAt: ZonedDateTime?,
) {
    companion object {
        /** 측정값으로부터 등급을 조립한다. 대표 등급은 pm10Grade·pm25Grade 중 더 나쁜 등급이다. */
        fun of(m: AirQualityMeasurement): AirQuality {
            val pm10Grade = AirQualityGrade.ofPm10(m.pm10)
            val pm25Grade = AirQualityGrade.ofPm25(m.pm25)
            return AirQuality(
                pm10 = m.pm10,
                pm25 = m.pm25,
                pm10Grade = pm10Grade,
                pm25Grade = pm25Grade,
                representativeGrade = AirQualityGrade.worseOf(pm10Grade, pm25Grade),
                stationName = m.stationName,
                measuredAt = m.measuredAt,
            )
        }

        /** 측정 불가(3단계 체인 전부 실패) 시 반환하는 빈 값. 모든 등급은 UNKNOWN. */
        fun empty(): AirQuality = AirQuality(
            pm10 = null,
            pm25 = null,
            pm10Grade = AirQualityGrade.UNKNOWN,
            pm25Grade = AirQualityGrade.UNKNOWN,
            representativeGrade = AirQualityGrade.UNKNOWN,
            stationName = null,
            measuredAt = null,
        )
    }
}
