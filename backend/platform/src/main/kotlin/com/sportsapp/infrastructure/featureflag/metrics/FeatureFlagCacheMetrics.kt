package com.sportsapp.infrastructure.featureflag.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry

/**
 * FeatureFlag 캐시 접근 지표 발신 공용 로직 (`feature_flag_cache_access_total{layer,result}`).
 *
 * L1(local)·L2(redis) 캐시 접근 지점 각각에서 동일한 카운터 등록 로직을 재사용한다.
 */
object FeatureFlagCacheMetrics {
    const val CACHE_ACCESS_COUNTER = "feature_flag_cache_access_total"
    const val PROPAGATION_LAG_TIMER = "feature_flag_propagation_lag_seconds"

    const val LAYER_LOCAL = "local"
    const val LAYER_REDIS = "redis"

    private const val LAYER_TAG = "layer"
    private const val RESULT_TAG = "result"
    private const val RESULT_HIT = "hit"
    private const val RESULT_MISS = "miss"

    fun recordCacheAccess(meterRegistry: MeterRegistry, layer: String, hit: Boolean) {
        Counter.builder(CACHE_ACCESS_COUNTER)
            .tag(LAYER_TAG, layer)
            .tag(RESULT_TAG, if (hit) RESULT_HIT else RESULT_MISS)
            .register(meterRegistry)
            .increment()
    }
}
