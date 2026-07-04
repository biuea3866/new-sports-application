package com.sportsapp.domain.airquality.vo

/**
 * CAI(통합대기환경지수) 4단계 등급 — PM10/PM2.5 농도로부터 산출한다 (TDD.md FR-11).
 */
enum class AirQualityGrade {
    GOOD,
    MODERATE,
    BAD,
    VERY_BAD,
    UNKNOWN,
    ;

    /** BAD 이상(BAD·VERY_BAD)인지 질의한다. */
    fun isBadOrWorse(): Boolean = this == BAD || this == VERY_BAD

    companion object {
        private const val PM10_GOOD_MAX = 30
        private const val PM10_MODERATE_MAX = 80
        private const val PM10_BAD_MAX = 150

        private const val PM25_GOOD_MAX = 15
        private const val PM25_MODERATE_MAX = 35
        private const val PM25_BAD_MAX = 75

        /** PM10 농도(㎍/㎥)를 등급화한다. null이면 UNKNOWN. */
        fun ofPm10(value: Int?): AirQualityGrade {
            if (value == null) return UNKNOWN
            return when {
                value <= PM10_GOOD_MAX -> GOOD
                value <= PM10_MODERATE_MAX -> MODERATE
                value <= PM10_BAD_MAX -> BAD
                else -> VERY_BAD
            }
        }

        /** PM2.5 농도(㎍/㎥)를 등급화한다. null이면 UNKNOWN. */
        fun ofPm25(value: Int?): AirQualityGrade {
            if (value == null) return UNKNOWN
            return when {
                value <= PM25_GOOD_MAX -> GOOD
                value <= PM25_MODERATE_MAX -> MODERATE
                value <= PM25_BAD_MAX -> BAD
                else -> VERY_BAD
            }
        }

        /** 두 등급 중 더 나쁜 등급을 대표 등급으로 선정한다. UNKNOWN은 상대가 알려진 등급이면 배제한다. */
        fun worseOf(a: AirQualityGrade, b: AirQualityGrade): AirQualityGrade {
            if (a == UNKNOWN) return b
            if (b == UNKNOWN) return a
            return if (a.severity() >= b.severity()) a else b
        }

        private fun AirQualityGrade.severity(): Int = when (this) {
            GOOD -> 0
            MODERATE -> 1
            BAD -> 2
            VERY_BAD -> 3
            UNKNOWN -> -1
        }
    }
}
