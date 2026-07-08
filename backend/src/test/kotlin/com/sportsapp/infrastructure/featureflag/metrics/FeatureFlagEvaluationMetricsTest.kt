package com.sportsapp.infrastructure.featureflag.metrics

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry

/**
 * `FeatureFlagEvaluationMetrics` — 노출(평가) 카운터(`feature_flag_evaluations_total{key}`, FR-13) 검증.
 */
class FeatureFlagEvaluationMetricsTest : BehaviorSpec({

    Given("동일 key로 평가가 2회 발생하면") {
        val meterRegistry = SimpleMeterRegistry()

        When("recordEvaluation을 2회 호출하면") {
            FeatureFlagEvaluationMetrics.recordEvaluation(meterRegistry, "demo.feature.hello")
            FeatureFlagEvaluationMetrics.recordEvaluation(meterRegistry, "demo.feature.hello")

            Then("feature_flag_evaluations_total{key} 카운터가 2 증가한다") {
                meterRegistry.counter(
                    FeatureFlagEvaluationMetrics.EVALUATIONS_COUNTER,
                    "key",
                    "demo.feature.hello",
                ).count() shouldBe 2.0
            }
        }
    }

    Given("서로 다른 key로 평가가 각 1회 발생하면") {
        val meterRegistry = SimpleMeterRegistry()

        When("recordEvaluation을 key별로 호출하면") {
            FeatureFlagEvaluationMetrics.recordEvaluation(meterRegistry, "demo.feature.a")
            FeatureFlagEvaluationMetrics.recordEvaluation(meterRegistry, "demo.feature.b")

            Then("key별로 독립된 카운터가 각 1씩 증가한다") {
                meterRegistry.counter(FeatureFlagEvaluationMetrics.EVALUATIONS_COUNTER, "key", "demo.feature.a")
                    .count() shouldBe 1.0
                meterRegistry.counter(FeatureFlagEvaluationMetrics.EVALUATIONS_COUNTER, "key", "demo.feature.b")
                    .count() shouldBe 1.0
            }
        }
    }
})
