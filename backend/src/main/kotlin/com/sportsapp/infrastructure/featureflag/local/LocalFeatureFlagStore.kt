package com.sportsapp.infrastructure.featureflag.local

import com.sportsapp.domain.featureflag.gateway.FeatureFlagCacheStore
import com.sportsapp.domain.featureflag.repository.FeatureFlagRepository
import com.sportsapp.domain.featureflag.vo.FeatureFlagSnapshot
import com.sportsapp.infrastructure.featureflag.metrics.FeatureFlagCacheMetrics
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 인스턴스 로컬 L1 캐시(ConcurrentHashMap) — Redis(L2) 미스 시 MySQL(SSOT)로 폴백한다.
 */
@Component
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
     * 옵저버빌리티(BE-12) 게이지 `feature_flag_local_snapshot_size` 전용 읽기 전용 접근자.
     * 로컬에 적재된 스냅샷 수를 그대로 노출한다 — 동작 변경 없음.
     */
    fun size(): Int = snapshots.size

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
