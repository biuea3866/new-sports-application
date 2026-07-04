package com.sportsapp.infrastructure.featureflag.local

import com.sportsapp.domain.featureflag.gateway.FeatureFlagCacheStore
import com.sportsapp.domain.featureflag.repository.FeatureFlagRepository
import com.sportsapp.domain.featureflag.vo.FeatureFlagSnapshot
import com.sportsapp.infrastructure.featureflag.metrics.FeatureFlagCacheMetrics
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 인스턴스 로컬 L1 캐시(ConcurrentHashMap) — Redis(L2) 미스 시 MySQL(SSOT)로 폴백한다.
 *
 * `@ConditionalOnBean(FeatureFlagRepository::class)`: 이 워크트리(BE-03)는 BE-02(MySQL 영속화)의
 * `FeatureFlagRepository` 구현체를 포함하지 않는다(런타임 의존, 티켓 명시). 구현체가 없는 상태로
 * 전체 애플리케이션 컨텍스트를 로드하면 필수 생성자 의존성 미충족으로 기동이 실패하므로, 구현체가
 * 클래스패스에 존재할 때만(BE-02/BE-10 통합 시점) 이 빈 클러스터(LocalFeatureFlagStore·
 * FeatureFlagChangeSubscriber·FeatureFlagRedisPubSubConfig)가 활성화되도록 조건을 건다.
 */
@Component
@ConditionalOnBean(FeatureFlagRepository::class)
class LocalFeatureFlagStore(
    private val cacheStore: FeatureFlagCacheStore,
    private val featureFlagRepository: FeatureFlagRepository,
    private val meterRegistry: MeterRegistry,
) {

    private val logger = LoggerFactory.getLogger(LocalFeatureFlagStore::class.java)
    private val snapshots = ConcurrentHashMap<String, FeatureFlagSnapshot>()

    fun get(key: String): FeatureFlagSnapshot? {
        val snapshot = snapshots[key]
        FeatureFlagCacheMetrics.recordCacheAccess(meterRegistry, FeatureFlagCacheMetrics.LAYER_LOCAL, hit = snapshot != null)
        return snapshot
    }

    /**
     * 재조회 후 덮어쓰기 — 별도 멱등 키 없이 멱등(같은 값을 반복 반영해도 최종 상태 동일).
     * Redis 캐시를 먼저 조회하고, 미스면 Repository(MySQL, SSOT)로 폴백한다.
     */
    fun refresh(key: String) {
        val snapshot = cacheStore.get(key) ?: featureFlagRepository.findByKey(key)?.toSnapshot() ?: return
        snapshots[key] = snapshot
        cacheStore.put(snapshot)
    }

    @PostConstruct
    fun bootstrap() {
        val activeFlags = featureFlagRepository.findAllActive()
        activeFlags.forEach { flag ->
            val snapshot = flag.toSnapshot()
            snapshots[snapshot.key] = snapshot
            cacheStore.put(snapshot)
        }
        logger.info("LocalFeatureFlagStore bootstrap warmed {} flag(s)", activeFlags.size)
    }

    /**
     * pub/sub 유실(구독 끊김 등) 안전망 — 30초 주기로 로컬에 적재된 전체 key를 재수렴시킨다.
     */
    @Scheduled(fixedDelay = SCHEDULED_REFRESH_INTERVAL_MS)
    fun scheduledRefreshAll() {
        snapshots.keys.toList().forEach { refresh(it) }
    }

    companion object {
        private const val SCHEDULED_REFRESH_INTERVAL_MS = 30_000L
    }
}
