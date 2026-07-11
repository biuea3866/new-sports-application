package com.sportsapp.domain.virtualqueue

/**
 * 가상 대기열 관련 피처 플래그 키 상수. `FeatureFlagEvaluator.isEnabled(key, context, default)`가
 * 소비하는 문자열 키를 한 곳에 모아 매직 스트링 중복을 막는다.
 *
 * 플래그는 부팅 토글(`@ConditionalOnProperty`)이 아니라 **런타임 조회**로 분기한다
 * (no-conditional-on-property) — OFF(기본값)면 `EnterQueueUseCase`/`EntryTokenGateInterceptor`
 * 양쪽 모두 대기열을 우회해 즉시 통과시킨다. 플래그 값 자체는 이 상수가 아니라
 * `FeatureFlagEvaluator` 구현체(featureflag 도메인)가 관리한다.
 */
object VirtualQueueFeatureFlagKeys {
    /** 대기열 경유 여부. OFF(기본값)면 `QueueStatus.directEntry`로 즉시 통과한다. */
    const val ENABLED = "virtual-queue.enabled"

    /**
     * Admission Pump 운영 킬 스위치. ON(기본값)이면 `AdmissionPumpScheduler`가 매 틱 배치를
     * 실행하고, 관리 플래그를 OFF로 활성화하면 재기동 없이 다음 틱부터 배치 실행을 건너뛴다.
     */
    const val ADMISSION_ENABLED = "virtual-queue.admission.enabled"
}
