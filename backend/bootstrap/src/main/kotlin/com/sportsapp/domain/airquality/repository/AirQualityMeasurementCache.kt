package com.sportsapp.domain.airquality.repository

import com.sportsapp.domain.airquality.vo.AirQualityMeasurement

/**
 * 그리드키 단위 측정 결과 캐시 계약 (TDD.md §인터페이스 시그니처). 구현체는 infrastructure의
 * Redis 기반 `AirQualityMeasurementCacheImpl`이 담당한다.
 */
interface AirQualityMeasurementCache {

    /** [gridKey]에 캐시된 측정값을 조회한다. 캐시 미스 시 null. */
    fun findBy(gridKey: String): AirQualityMeasurement?

    /** [gridKey]에 [measurement]를 캐시한다. */
    fun save(gridKey: String, measurement: AirQualityMeasurement)
}
