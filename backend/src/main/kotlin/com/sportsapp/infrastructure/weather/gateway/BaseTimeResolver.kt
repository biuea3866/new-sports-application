package com.sportsapp.infrastructure.weather.gateway

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 기상청 단기예보 발표 기준시각(base_date, base_time) 계산.
 * 발표 시각: 02,05,08,11,14,17,20,23시. 발표 후 ~10분 뒤 제공되므로 lag 를 둔다.
 * 현재 시각보다 이른 가장 최근 발표시각을 고른다. 02:10 이전이면 전일 23시.
 */
object BaseTimeResolver {
    private val BASE_HOURS = listOf(2, 5, 8, 11, 14, 17, 20, 23)
    private const val AVAILABILITY_LAG_MINUTES = 10
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun resolve(now: ZonedDateTime): BaseDateTime {
        val minutesNow = now.hour * 60 + now.minute - AVAILABILITY_LAG_MINUTES
        val baseHour = BASE_HOURS.lastOrNull { it * 60 <= minutesNow }
        if (baseHour == null) {
            val yesterday = now.minusDays(1)
            return BaseDateTime(baseDate = yesterday.format(DATE_FORMAT), baseTime = "2300")
        }
        return BaseDateTime(
            baseDate = now.format(DATE_FORMAT),
            baseTime = "%02d00".format(baseHour),
        )
    }
}

data class BaseDateTime(
    val baseDate: String,
    val baseTime: String,
)
