package com.sportsapp.infrastructure.featureflag.metrics

import com.sportsapp.infrastructure.featureflag.local.LocalFeatureFlagStore
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.slf4j.LoggerFactory
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Component

/**
 * FeatureFlag Observability 게이지·부트스트랩 경보 전담 바인더(BE-12).
 *
 * BE-03/BE-06이 이미 발신 중인 카운터(`feature_flag_evaluations_total`,
 * `feature_flag_cache_access_total`)와 전파 지연 타이머(`feature_flag_propagation_lag_seconds`,
 * [FeatureFlagChangeSubscriber][com.sportsapp.infrastructure.featureflag.redis.FeatureFlagChangeSubscriber]가
 * 동일 [MeterRegistry] 인스턴스에 직접 등록)는 그대로 두고(BE-03/BE-06 파일 미수정), 본 클래스는
 * 아래 게이지만 신규 등록한다.
 *
 * - `feature_flag_local_snapshot_size`: [LocalFeatureFlagStore]에 적재된 스냅샷 수
 * - `feature_flag_bootstrap_success`: 부트스트랩 상태 확인 성공(1) / 실패(0)
 * - `feature_flag_pubsub_listener_active`: [RedisMessageListenerContainer] 기동 여부
 * - `feature_flag_pubsub_connections`: 전용 구독 커넥션 수(근사치 — 아래 제약 참고)
 *
 * 전파 지연은 BE-03이 이미 동일 이름(`feature_flag_propagation_lag_seconds`)의 Timer로 등록해
 * 두므로, 같은 이름으로 Gauge를 추가 등록하지 않는다 — Micrometer는 동일 이름에 서로 다른
 * 타입(Timer/Gauge)의 미터를 함께 등록하면 예외를 던진다. 대신 [propagationLagSeconds]로
 * 기존 값을 읽기만 한다("이미 발신된 값을 게이지로 읽는다" — 티켓 명시 대안).
 *
 * 제약: `RedisMessageListenerContainer`(spring-data-redis)는 구독 커넥션 개수를 공개 API로
 * 노출하지 않는다. 컨테이너는 등록된 모든 채널을 단일 전용 구독 커넥션에 다중화하므로,
 * 커넥션 수는 `isRunning` 여부로 근사한다(기동 시 1, 아니면 0) — 커넥션 누수(다중 연결)
 * 자체는 이 근사로는 탐지할 수 없다(후속 과제, Open Question으로 보고).
 */
@Component
class FeatureFlagMetricsBinder(
    private val meterRegistry: MeterRegistry,
    private val localFeatureFlagStore: LocalFeatureFlagStore,
    private val redisMessageListenerContainer: RedisMessageListenerContainer,
) {

    private val logger = LoggerFactory.getLogger(FeatureFlagMetricsBinder::class.java)
    private val bootstrapSuccess = AtomicInteger(BOOTSTRAP_UNVERIFIED)

    @PostConstruct
    fun bindGauges() {
        Gauge.builder(LOCAL_SNAPSHOT_SIZE_GAUGE, localFeatureFlagStore) { it.size().toDouble() }
            .description("LocalFeatureFlagStore에 적재된 플래그 스냅샷 수")
            .register(meterRegistry)

        Gauge.builder(BOOTSTRAP_SUCCESS_GAUGE, bootstrapSuccess) { it.get().toDouble() }
            .description("FeatureFlag 부트스트랩 상태 확인 성공(1) / 실패(0)")
            .register(meterRegistry)

        Gauge.builder(PUBSUB_LISTENER_ACTIVE_GAUGE, redisMessageListenerContainer) { if (it.isRunning) 1.0 else 0.0 }
            .description("FeatureFlag pub/sub RedisMessageListenerContainer 기동 여부")
            .register(meterRegistry)

        Gauge.builder(PUBSUB_CONNECTIONS_GAUGE, redisMessageListenerContainer) { if (it.isRunning) 1.0 else 0.0 }
            .description("FeatureFlag pub/sub 전용 구독 커넥션 수(근사)")
            .register(meterRegistry)

        verifyBootstrap()
    }

    /**
     * 게이지 등록 직후 로컬 스토어 상태를 재확인해 부트스트랩 검증 결과를 갱신한다.
     * 확인 자체가 실패하면(예: 로컬 스토어 초기화 이상) 실패로 간주해 critical 경보를 발신한다.
     */
    private fun verifyBootstrap() {
        runCatching { localFeatureFlagStore.size() }
            .onSuccess { bootstrapSuccess.set(BOOTSTRAP_SUCCESS) }
            .onFailure { exception ->
                bootstrapSuccess.set(BOOTSTRAP_FAILURE)
                logger.error(
                    "event=feature-flag-bootstrap-failed severity=critical source=feature-flag message={}",
                    exception.message,
                )
            }
    }

    /**
     * BE-03 subscriber가 이미 기록한 최근 전파 지연 값을 읽는다(재등록 아님).
     * 아직 한 건도 수신하지 않아 타이머 자체가 등록되지 않았다면 0.0을 반환한다.
     */
    fun propagationLagSeconds(): Double =
        meterRegistry.find(FeatureFlagCacheMetrics.PROPAGATION_LAG_TIMER).timer()?.mean(TimeUnit.SECONDS) ?: 0.0

    companion object {
        const val LOCAL_SNAPSHOT_SIZE_GAUGE = "feature_flag_local_snapshot_size"
        const val BOOTSTRAP_SUCCESS_GAUGE = "feature_flag_bootstrap_success"
        const val PUBSUB_LISTENER_ACTIVE_GAUGE = "feature_flag_pubsub_listener_active"
        const val PUBSUB_CONNECTIONS_GAUGE = "feature_flag_pubsub_connections"

        private const val BOOTSTRAP_UNVERIFIED = -1
        private const val BOOTSTRAP_SUCCESS = 1
        private const val BOOTSTRAP_FAILURE = 0
    }
}
