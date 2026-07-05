package com.sportsapp.infrastructure.featureflag.evaluator

import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.featureflag.strategy.FeatureEvaluation
import com.sportsapp.domain.featureflag.vo.FeatureFlagSnapshot
import com.sportsapp.infrastructure.featureflag.local.LocalFeatureFlagStore
import com.sportsapp.infrastructure.featureflag.metrics.FeatureFlagEvaluationMetrics
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 공용 평가 클라이언트(`common.FeatureFlagEvaluator`) 구현 — 폴백 체인(FR-11) + 노출 집계(FR-13).
 *
 * 폴백 체인: [LocalFeatureFlagStore.get] 히트 시 즉시 사용하고, 미스면
 * [LocalFeatureFlagStore.refresh](Redis → MySQL)로 채운 뒤 재조회한다. 스냅샷이 없거나(null)
 * ARCHIVED면 호출부 `default`를 반환하고, ACTIVE면 [FeatureFlagSnapshot.strategy]를 평가해
 * 위임한다 — `FeatureFlag.evaluate(context)`와 동일한 규칙(ARCHIVED 평가 제외 → 전략 위임)을
 * 스냅샷 기준으로 재현한다.
 *
 * Redis·MySQL 모두 접근 불가해도 로컬에 마지막 성공 스냅샷이 남아 있으면(= 이미 [LocalFeatureFlagStore.get]
 * 히트) refresh를 아예 시도하지 않고 그 값으로 평가를 지속한다. 로컬에도 없어 refresh를 시도했는데
 * 그마저 실패하면 예외를 잡아 `redis-degraded` 경보만 남기고 호출부로 전파하지 않는다(읽기 지속).
 */
@Component
class FeatureFlagEvaluatorImpl(
    private val localFeatureFlagStore: LocalFeatureFlagStore,
    private val meterRegistry: MeterRegistry,
) : FeatureFlagEvaluator {

    private val logger = LoggerFactory.getLogger(FeatureFlagEvaluatorImpl::class.java)

    override fun isEnabled(key: String, context: FeatureContext, default: Boolean): Boolean {
        return when (resolveEvaluation(key, context)) {
            null -> default
            FeatureEvaluation.On -> true
            FeatureEvaluation.Off -> false
            is FeatureEvaluation.Assigned -> true
        }
    }

    override fun variant(key: String, context: FeatureContext, default: String): String {
        return when (val evaluation = resolveEvaluation(key, context)) {
            is FeatureEvaluation.Assigned -> evaluation.variantName
            else -> default
        }
    }

    private fun resolveEvaluation(key: String, context: FeatureContext): FeatureEvaluation? {
        FeatureFlagEvaluationMetrics.recordEvaluation(meterRegistry, key)
        return resolveSnapshot(key)
            ?.takeIf { it.status.isEvaluable() }
            ?.let { snapshot -> snapshot.strategy.evaluate(snapshot.key, context) }
    }

    private fun resolveSnapshot(key: String): FeatureFlagSnapshot? {
        localFeatureFlagStore.get(key)?.let { return it }
        return try {
            localFeatureFlagStore.refresh(key)
            localFeatureFlagStore.get(key)
        } catch (exception: Exception) {
            emitRedisDegraded(key, exception)
            null
        }
    }

    private fun emitRedisDegraded(key: String, exception: Exception) {
        logger.warn(
            "event=redis-degraded level=warning source=feature-flag flagKey={} message={}",
            key,
            exception.message,
        )
    }
}
