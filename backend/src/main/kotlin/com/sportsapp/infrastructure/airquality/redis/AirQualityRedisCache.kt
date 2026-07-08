package com.sportsapp.infrastructure.airquality.redis

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.domain.airquality.repository.AirQualityMeasurementCache
import com.sportsapp.domain.airquality.vo.AirQualityMeasurement
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.random.Random

/**
 * 그리드키 단위 측정값 Redis 캐시 (레디스 키 계약 `redis-airquality-key-contract.md`).
 * 키 `airquality:measurement:{gridKey}`, String+JSON, TTL 10분±60초 jitter(매 저장 시 재계산).
 * Redis 장애(예외) 시 캐시를 건너뛰고 로그만 남긴다 — 정합성 보장 대상이 아니다(§5).
 */
@Component
class AirQualityRedisCache(
    private val stringRedisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : AirQualityMeasurementCache {

    private val logger = LoggerFactory.getLogger(AirQualityRedisCache::class.java)

    override fun findBy(gridKey: String): AirQualityMeasurement? {
        return try {
            val json = stringRedisTemplate.opsForValue().get(cacheKey(gridKey)) ?: return null
            objectMapper.readValue(json, AirQualityMeasurementCacheDto::class.java).toMeasurement()
        } catch (exception: Exception) {
            logger.warn("air quality cache read failed (gridKey={}): {}", gridKey, exception.message)
            null
        }
    }

    override fun save(gridKey: String, measurement: AirQualityMeasurement) {
        try {
            val json = objectMapper.writeValueAsString(AirQualityMeasurementCacheDto.of(measurement))
            stringRedisTemplate.opsForValue().set(cacheKey(gridKey), json, ttlWithJitter())
        } catch (exception: Exception) {
            logger.warn("air quality cache write failed (gridKey={}): {}", gridKey, exception.message)
        }
    }

    private fun cacheKey(gridKey: String): String = "airquality:measurement:$gridKey"

    private fun ttlWithJitter(): Duration =
        Duration.ofSeconds(BASE_TTL_SECONDS + Random.nextLong(-JITTER_SECONDS, JITTER_SECONDS + 1))

    companion object {
        private const val BASE_TTL_SECONDS = 600L
        private const val JITTER_SECONDS = 60L
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AirQualityMeasurementCacheDto(
    val pm10: Int? = null,
    val pm25: Int? = null,
    val stationName: String? = null,
    val measuredAt: String? = null,
) {
    fun toMeasurement(): AirQualityMeasurement = AirQualityMeasurement(
        pm10 = pm10,
        pm25 = pm25,
        stationName = stationName,
        measuredAt = measuredAt?.let { ZonedDateTime.parse(it) },
    )

    companion object {
        fun of(measurement: AirQualityMeasurement): AirQualityMeasurementCacheDto = AirQualityMeasurementCacheDto(
            pm10 = measurement.pm10,
            pm25 = measurement.pm25,
            stationName = measurement.stationName,
            measuredAt = measurement.measuredAt?.toString(),
        )
    }
}
