package com.sportsapp.infrastructure.goods.redis

import com.sportsapp.domain.goods.gateway.DropReservationStore
import com.sportsapp.domain.goods.gateway.PendingReservation
import com.sportsapp.domain.goods.gateway.RejectCounts
import com.sportsapp.domain.goods.gateway.RejectKind
import com.sportsapp.domain.goods.gateway.ReservationResult
import io.micrometer.core.instrument.MeterRegistry
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

/**
 * `DropReservationStore` Redis 구현 — Lua 입장 게이트(FR-8·FR-6·멱등) + 인프로세스 세마포어 완충(FR-7).
 *
 * 키·TTL·Lua 계약: `backend/docs/redis/limited-drop-keys.md` (ADR-003).
 * `SeatLockStoreImpl`(`RedisDistributedLock`의 `DefaultRedisScript` 로딩 패턴) 선례를 따른다.
 *
 * 판정 순서(ADR-003): `reserve.lua`가 멱등 마커 → 1인 한도 → 소진 판정을 원자적으로 처리한다.
 * 완충(FR-7)은 이제 `reserve()`에 포함되지 않는다 — [tryAcquireThrottle]/[releaseThrottle]로 분리해,
 * Redis 장애로 `reserve()` 자체가 예외를 던지는 fail-open 경로도 DB 쓰기 전에 완충 게이트를 통과하도록
 * 호출부(`LimitedDropDomainService`)가 별도로 판정한다 (코드 리뷰 p1 — 완충 세마포어 우회 수정).
 *
 * 세마포어는 단일 인스턴스 전제(TDD Open Questions) — 다중 인스턴스 확장 시 Redis 토큰 버킷으로 승격 필요.
 *
 * Redis 인프라 장애(`DataAccessException`)는 여기서 삼키지 않고 그대로 전파한다.
 * 호출부(`LimitedDropDomainService`)가 fail-open 폴백을 처리한다.
 */
@Component
class DropReservationStoreImpl(
    private val redisTemplate: StringRedisTemplate,
    private val meterRegistry: MeterRegistry,
    @Value("\${app.limited-drop.reservation.semaphore-permits:200}")
    private val semaphorePermits: Int,
    @Value("\${app.limited-drop.reservation.acquire-timeout-millis:200}")
    private val acquireTimeoutMillis: Long,
    @Value("\${app.limited-drop.reservation.marker-ttl-seconds:600}")
    private val markerTtlSeconds: Long,
) : DropReservationStore {

    private val log = LoggerFactory.getLogger(DropReservationStoreImpl::class.java)

    private val admissionSemaphore = Semaphore(semaphorePermits)

    private val reserveScript: DefaultRedisScript<Long> = DefaultRedisScript(
        loadScript("redis/reserve.lua"),
        Long::class.java,
    )

    private val cancelScript: DefaultRedisScript<Long> = DefaultRedisScript(
        loadScript("redis/cancel.lua"),
        Long::class.java,
    )

    override fun seedIfAbsent(dropId: Long, initialQuantity: Int, ttl: Duration) {
        redisTemplate.opsForValue().setIfAbsent(remainingKey(dropId), initialQuantity.toString(), ttl)
    }

    override fun reserve(
        dropId: Long,
        userId: Long,
        quantity: Int,
        perUserLimit: Int,
        idempotencyKey: String,
    ): ReservationResult {
        val code = executeReserveScriptTracked(dropId, userId, quantity, perUserLimit, idempotencyKey)
        return when (code) {
            RESERVE_ADMITTED -> ReservationResult.Admitted
            RESERVE_SOLD_OUT -> ReservationResult.SoldOut.also { incrementRejectMetric(REJECT_KIND_SOLD_OUT) }
            RESERVE_ALREADY_RESERVED -> ReservationResult.AlreadyReserved
            RESERVE_PER_USER_LIMIT_EXCEEDED ->
                ReservationResult.PerUserLimitExceeded(perUserLimit).also { incrementRejectMetric(REJECT_KIND_PER_USER) }
            else -> error("reserve.lua 예상 밖 반환 코드: $code (dropId=$dropId)")
        }
    }

    /**
     * 완충(FR-7) permit 획득. [reserve] 성공·실패(fail-open)와 무관하게 DB 쓰기 직전 호출부가 호출한다.
     * 실패 시 throttled 거부 지표를 증가시킨다 — 복원(cancel)은 호출부 책임이다(Admitted 여부를 알아야 하므로).
     */
    override fun tryAcquireThrottle(): Boolean {
        val acquired = admissionSemaphore.tryAcquire(acquireTimeoutMillis, TimeUnit.MILLISECONDS)
        if (!acquired) incrementRejectMetric(REJECT_KIND_THROTTLED)
        return acquired
    }

    override fun releaseThrottle() {
        admissionSemaphore.release()
    }

    /** 현재는 별도 상태 변경이 필요 없다 — 완충 permit 반납은 [releaseThrottle]이 전담한다. */
    override fun confirmSuccess(dropId: Long, userId: Long, idempotencyKey: String) = Unit

    override fun cancel(dropId: Long, userId: Long, quantity: Int, idempotencyKey: String) {
        executeCancelScript(dropId, userId, quantity, idempotencyKey)
    }

    /**
     * FIX-04 대사 전용 복원 — [cancel]과 동일한 [executeCancelScript]를 재사용하되, 반환 코드로
     * 실제 복원 여부(Restored/NoOp)를 판별해 되돌려준다. 새 Lua 스크립트를 추가하지 않는다.
     */
    override fun restoreOrphanedReservation(dropId: Long, userId: Long, quantity: Int, idempotencyKey: String): Boolean =
        executeCancelScript(dropId, userId, quantity, idempotencyKey) == CANCEL_RESTORED

    /**
     * [dropId]의 예약 마커를 `SCAN`으로 열거하고(`KEYS` 금지), 마커 생성 후 [graceSeconds] 이상
     * 경과한(=TTL이 `markerTtlSeconds - graceSeconds` 이하로 남은) 예약만 반환한다. TTL 조회·값
     * 조회 사이 레이스로 키가 사라지거나(만료·복원 완료), 마커 값이 파싱 불가한 레거시 포맷("1",
     * ARGV[4] 도입 이전 예약)이면 조용히 건너뛴다 — 대사는 관측용이라 한 건의 이상으로 전체 스캔이
     * 실패하면 안 된다.
     *
     * 레거시 포맷 skip은 [LEGACY_MARKER_SKIPPED_COUNTER]로 관측한다(조용한 통과 방지). 배포 직후
     * `markerTtlSeconds`(기본 600초) 동안은 ARGV[4] 도입 이전 마커가 남아 있어 0이 아닐 수 있으나,
     * 그 시간이 지나면 구 포맷 마커가 모두 자연 만료돼 0으로 수렴한다 — 배포 후 markerTtlSeconds가
     * 지난 시점에도 이 값이 계속 0이 아니면 별도 조사가 필요하다.
     */
    override fun scanStaleReservations(dropId: Long, graceSeconds: Long): List<PendingReservation> {
        val prefix = reservedKeyPrefix(dropId)
        val staleThresholdTtlSeconds = (markerTtlSeconds - graceSeconds).coerceAtLeast(0)
        return scanKeys("$prefix*").mapNotNull { key -> toPendingReservationIfStale(key, prefix, staleThresholdTtlSeconds) }
    }

    private fun toPendingReservationIfStale(key: String, prefix: String, staleThresholdTtlSeconds: Long): PendingReservation? {
        val ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS)
        if (ttlSeconds < 0 || ttlSeconds > staleThresholdTtlSeconds) return null
        val value = redisTemplate.opsForValue().get(key) ?: return null
        val (userId, quantity) = parseMarkerValue(value) ?: run {
            recordLegacyMarkerSkipped(key)
            return null
        }
        return PendingReservation(idempotencyKey = key.removePrefix(prefix), userId = userId, quantity = quantity)
    }

    private fun parseMarkerValue(value: String): Pair<Long, Int>? {
        val parts = value.split(':')
        if (parts.size != 2) return null
        val userId = parts[0].toLongOrNull() ?: return null
        val quantity = parts[1].toIntOrNull() ?: return null
        return userId to quantity
    }

    /**
     * ARGV[4] 도입 이전 레거시 포맷 마커(값 "1")는 userId·quantity가 없어 [restoreOrphanedReservation]에
     * 필요한 인자를 구성할 수 없다 — 복원 대상에서 조용히 제외하지 않도록 지표·로그로 남긴다.
     * 값 전체는 찍지 않는다(마커 키만 남긴다).
     */
    private fun recordLegacyMarkerSkipped(key: String) {
        log.warn("DropReservationStoreImpl: 레거시 포맷 마커라 언더셀 대사에서 건너뜁니다 key={}", key)
        meterRegistry.counter(LEGACY_MARKER_SKIPPED_COUNTER).increment()
    }

    /**
     * `SCAN MATCH pattern`으로 키를 열거한다 — 프로덕션 블로킹을 피하기 위해 `KEYS`를 쓰지 않는다.
     * `RedisConnection`은 `java.io.Closeable`이 아니라 `AutoCloseable`이라 `use{}`를 쓸 수 없어
     * try/finally로 직접 닫는다(`Cursor`는 `Closeable`이라 `use{}`를 그대로 쓴다).
     */
    private fun scanKeys(pattern: String): List<String> {
        val keys = mutableListOf<String>()
        val options = ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH_SIZE).build()
        val connectionFactory = requireNotNull(redisTemplate.connectionFactory) { "RedisConnectionFactory 를 사용할 수 없습니다" }
        val connection = connectionFactory.connection
        try {
            connection.scan(options).use { cursor ->
                while (cursor.hasNext()) {
                    keys += String(cursor.next())
                }
            }
        } finally {
            connection.close()
        }
        return keys
    }

    override fun remaining(dropId: Long): Int? =
        redisTemplate.opsForValue().get(remainingKey(dropId))?.toIntOrNull()

    /**
     * FR-9 거부 카운터를 증가시키고, TTL을 [remainingKey]와 동일하게 정렬한다(O(1), hot path 부담 미미).
     * `remaining` 키가 시드되지 않았거나 TTL이 없으면 [alignTtlWithRemaining]이 기본 TTL을 부여한다.
     */
    override fun recordReject(dropId: Long, kind: RejectKind) {
        val key = rejectKey(dropId, kind)
        redisTemplate.opsForValue().increment(key)
        alignTtlWithRemaining(dropId, key)
    }

    override fun rejectCounts(dropId: Long): RejectCounts = RejectCounts(
        soldOutCount = countAt(rejectKey(dropId, RejectKind.SOLD_OUT)),
        tooEarlyCount = countAt(rejectKey(dropId, RejectKind.TOO_EARLY)),
    )

    private fun countAt(key: String): Long = redisTemplate.opsForValue().get(key)?.toLongOrNull() ?: 0L

    /**
     * 거부 카운터 TTL을 remaining 키에 정렬한다. remaining 키에 TTL이 없거나(-1) 아직 시드되지 않아
     * 존재하지 않으면(-2) 거부 카운터가 무TTL로 잔존해 회차마다 누적되므로(인프라 리뷰 p3),
     * [markerTtlSeconds]를 기본 TTL로 부여한다.
     */
    private fun alignTtlWithRemaining(dropId: Long, key: String) {
        val remainingTtlSeconds = redisTemplate.getExpire(remainingKey(dropId), TimeUnit.SECONDS)
        if (remainingTtlSeconds > 0) {
            redisTemplate.expire(key, remainingTtlSeconds, TimeUnit.SECONDS)
            return
        }
        redisTemplate.expire(key, markerTtlSeconds, TimeUnit.SECONDS)
    }

    /**
     * [executeReserveScript]를 Redis 인프라 장애 관측 지표로 감싼다. `DataAccessException`은
     * 여기서 삼키지 않고 그대로 재전파한다 — 호출부([LimitedDropDomainService])의 fail-open 판단은 불변,
     * 이 지점에서는 [redis-degraded][REDIS_DEGRADED_COUNTER] 카운터만 증가시킨다.
     */
    private fun executeReserveScriptTracked(
        dropId: Long,
        userId: Long,
        quantity: Int,
        perUserLimit: Int,
        idempotencyKey: String,
    ): Long = try {
        executeReserveScript(dropId, userId, quantity, perUserLimit, idempotencyKey)
    } catch (exception: DataAccessException) {
        meterRegistry.counter(REDIS_DEGRADED_COUNTER).increment()
        throw exception
    }

    private fun executeReserveScript(
        dropId: Long,
        userId: Long,
        quantity: Int,
        perUserLimit: Int,
        idempotencyKey: String,
    ): Long {
        val keys = listOf(remainingKey(dropId), buyerKey(dropId, userId), reservedKey(dropId, idempotencyKey))
        return requireNotNull(
            redisTemplate.execute(
                reserveScript,
                keys,
                quantity.toString(),
                perUserLimit.toString(),
                markerTtlSeconds.toString(),
                userId.toString(),
            ),
        ) { "reserve.lua 실행 결과가 null (dropId=$dropId)" }
    }

    private fun incrementRejectMetric(kind: String) {
        meterRegistry.counter(REJECT_COUNTER, REJECT_TAG_KIND, kind).increment()
    }

    /**
     * `cancel.lua`를 실행하고 반환 코드를 그대로 넘긴다 — [cancel]은 이 코드를 무시하고(기존 계약
     * 유지), [restoreOrphanedReservation](FIX-04)은 이 코드로 실제 복원 여부를 판별한다.
     */
    private fun executeCancelScript(dropId: Long, userId: Long, quantity: Int, idempotencyKey: String): Long {
        val keys = listOf(remainingKey(dropId), buyerKey(dropId, userId), reservedKey(dropId, idempotencyKey))
        return redisTemplate.execute(cancelScript, keys, quantity.toString()) ?: CANCEL_NOOP
    }

    private fun remainingKey(dropId: Long) = "goods:limited-drop:$dropId:remaining"

    private fun buyerKey(dropId: Long, userId: Long) = "goods:limited-drop:$dropId:buyer:$userId"

    private fun reservedKey(dropId: Long, idempotencyKey: String) = "${reservedKeyPrefix(dropId)}$idempotencyKey"

    private fun reservedKeyPrefix(dropId: Long) = "goods:limited-drop:$dropId:reserved:"

    private fun rejectKey(dropId: Long, kind: RejectKind): String {
        val suffix = when (kind) {
            RejectKind.SOLD_OUT -> "sold-out"
            RejectKind.TOO_EARLY -> "too-early"
        }
        return "goods:limited-drop:$dropId:reject:$suffix"
    }

    private fun loadScript(classpath: String): String =
        ClassPathResource(classpath).inputStream.bufferedReader().use { it.readText() }

    companion object {
        private const val RESERVE_ADMITTED = 1L
        private const val RESERVE_SOLD_OUT = 0L
        private const val RESERVE_ALREADY_RESERVED = 2L
        private const val RESERVE_PER_USER_LIMIT_EXCEEDED = 3L

        /** cancel.lua 반환 코드 (FIX-04 — [restoreOrphanedReservation]이 판별에 사용). */
        private const val CANCEL_RESTORED = 1L
        private const val CANCEL_NOOP = 0L

        /** FIX-04 [scanKeys] SCAN COUNT 힌트 — 배치 크기. */
        private const val SCAN_BATCH_SIZE = 200L

        /** ⑦ 대시보드 지표 (Observability, BE-11). */
        private const val REJECT_COUNTER = "limited_drop.reject"
        private const val REJECT_TAG_KIND = "kind"
        private const val REJECT_KIND_SOLD_OUT = "sold_out"
        private const val REJECT_KIND_THROTTLED = "throttled"
        private const val REJECT_KIND_PER_USER = "per_user"
        private const val REDIS_DEGRADED_COUNTER = "limited_drop.redis_degraded"

        /** FIX-04 — ARGV[4] 도입 이전 레거시 포맷 마커라 언더셀 복원 대상에서 제외된 건수. */
        private const val LEGACY_MARKER_SKIPPED_COUNTER = "limited_drop.legacy_marker_skipped"
    }
}
