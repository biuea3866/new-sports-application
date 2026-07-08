package com.sportsapp.domain.common

/**
 * 피처 플래그 공용 평가 진입점.
 *
 * 소비 도메인은 이 인터페이스(및 [FeatureContext])만 주입해 평가한다 —
 * `domain.featureflag`를 import하지 않아 도메인 교차 참조를 피한다.
 *
 * 구현체(`FeatureFlagEvaluatorImpl`, infrastructure)는 로컬 스냅샷 → Redis → MySQL →
 * [default] 순으로 폴백하며, 정의되지 않은 key·ARCHIVED 플래그는 항상 [default]를 반환한다.
 */
interface FeatureFlagEvaluator {
    fun isEnabled(key: String, context: FeatureContext, default: Boolean): Boolean
    fun variant(key: String, context: FeatureContext, default: String): String
}
