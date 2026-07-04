package com.sportsapp.domain.common

/**
 * 피처 플래그 평가 입력 컨텍스트.
 *
 * `domain.featureflag`를 직접 참조하지 않고도 모든 도메인이 평가를 요청할 수 있도록
 * `domain.common`에 배치한다 (선례: [DistributedLock], [PermissionRepository]).
 *
 * @param userId 안정 키(stable key) — 퍼센티지 롤아웃·EXPERIMENT variant 배정의 버케팅 입력.
 * null이면 해당 전략들은 평가를 제외하고 Off를 반환한다.
 * @param attributes AttributeMatch 전략이 참조하는 속성 맵 (예: "plan" to "PREMIUM")
 */
data class FeatureContext(
    val userId: Long?,
    val attributes: Map<String, String> = emptyMap(),
) {
    companion object {
        fun of(userId: Long?): FeatureContext = FeatureContext(userId = userId)
        fun anonymous(): FeatureContext = FeatureContext(userId = null)
    }
}
