package com.sportsapp.infrastructure.featureflag.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry

/**
 * FeatureFlag 노출(평가) 지표 발신 공용 로직 (`feature_flag_evaluations_total{key}`, FR-13).
 *
 * [com.sportsapp.infrastructure.featureflag.evaluator.FeatureFlagEvaluatorImpl]가 평가 1회당
 * 1씩 증가시킨다 — 평가 결과(on/off/변형)와 무관하게 호출 자체를 노출로 집계한다.
 */
object FeatureFlagEvaluationMetrics {
    const val EVALUATIONS_COUNTER = "feature_flag_evaluations_total"

    private const val KEY_TAG = "key"

    fun recordEvaluation(meterRegistry: MeterRegistry, key: String) {
        Counter.builder(EVALUATIONS_COUNTER)
            .tag(KEY_TAG, key)
            .register(meterRegistry)
            .increment()
    }
}
