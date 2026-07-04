package com.sportsapp.domain.featureflag.entity

/**
 * 피처 플래그 상태. ACTIVE ↔ ARCHIVED 만 상호 전이 가능하다.
 */
enum class FeatureFlagStatus {
    ACTIVE,
    ARCHIVED,
    ;

    fun canTransitTo(target: FeatureFlagStatus): Boolean = when (this) {
        ACTIVE -> target == ARCHIVED
        ARCHIVED -> target == ACTIVE
    }

    /**
     * ARCHIVED 플래그는 평가 대상에서 제외된다 (호출부 기본값으로 폴백).
     */
    fun isEvaluable(): Boolean = this == ACTIVE
}
