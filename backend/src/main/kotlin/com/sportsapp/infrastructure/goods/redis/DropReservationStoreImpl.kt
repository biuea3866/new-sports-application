package com.sportsapp.infrastructure.goods.redis

import com.sportsapp.domain.goods.gateway.DropReservationStore
import com.sportsapp.domain.goods.gateway.RejectCounts
import com.sportsapp.domain.goods.gateway.RejectKind
import com.sportsapp.domain.goods.gateway.ReservationResult
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

/**
 * `DropReservationStore` Redis 구현 — Lua 입장 게이트(FR-8·FR-6·멱등) + 인프로세스 세마포어 완충(FR-7).
 *
 * 키·TTL·Lua 계약: `backend/docs/redis/limited-drop-keys.md` (ADR-003).
 * `SeatLockStoreImpl`(`RedisDistributedLock`의 `DefaultRedisScript` 로딩 패턴) 선례를 따른다.
 *
 * 판정 순서(ADR-003): `reserve.lua`가 멱등 마커 → 1인 한도 → 소진 판정을 원자적으로 처리해
 * `Admitted`(1)를 반환하면, 그다음 인프로세스 [admissionSemaphore]로 DB 동시 쓰기를 완충한다.
 * permit 획득 실패 시 `cancel.lua`로 Redis 상태를 즉시 복원하고 [ReservationResult.Throttled]를 반환한다.
 *
 * 세마포어는 단일 인스턴스 전제(TDD Open Questions) — 다중 인스턴스 확장 시 Redis 토큰 버킷으로 승격 필요.
 *
 * Redis 인프라 장애(`DataAccessException`)는 여기서 삼키지 않고 그대로 전파한다.
 * 호출부(`LimitedDropDomainService`)가 fail-open 폴백을 처리한다.
 */
@Component
class DropReservationStoreImpl(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${app.limited-drop.reservation.semaphore-permits:200}")
    private val semaphorePermits: Int,
    @Value("\${app.limited-drop.reservation.acquire-timeout-millis:200}")
    private val acquireTimeoutMillis: Long,
    @Value("\${app.limited-drop.reservation.marker-ttl-seconds:600}")
    private val markerTtlSeconds: Long,
) : DropReservationStore {

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
        val code = executeReserveScript(dropId, userId, quantity, perUserLimit, idempotencyKey)
        return when (code) {
            RESERVE_ADMITTED -> admitWithSemaphore(dropId, userId, quantity, idempotencyKey)
            RESERVE_SOLD_OUT -> ReservationResult.SoldOut
            RESERVE_ALREADY_RESERVED -> ReservationResult.AlreadyReserved
            RESERVE_PER_USER_LIMIT_EXCEEDED -> ReservationResult.PerUserLimitExceeded(perUserLimit)
            else -> error("reserve.lua 예상 밖 반환 코드: $code (dropId=$dropId)")
        }
    }

    override fun confirmSuccess(dropId: Long, userId: Long, idempotencyKey: String) {
        admissionSemaphore.release()
    }

    override fun cancel(dropId: Long, userId: Long, quantity: Int, idempotencyKey: String) {
        executeCancelScript(dropId, userId, quantity, idempotencyKey)
        admissionSemaphore.release()
    }

    override fun remaining(dropId: Long): Int? =
        redisTemplate.opsForValue().get(remainingKey(dropId))?.toIntOrNull()

    /**
     * FR-9 거부 카운터를 증가시키고, TTL을 [remainingKey]와 동일하게 정렬한다(O(1), hot path 부담 미미).
     * `remaining` 키가 시드되지 않았거나 만료 없이 유지 중이면 TTL을 별도로 설정하지 않는다.
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

    private fun alignTtlWithRemaining(dropId: Long, key: String) {
        val remainingTtlSeconds = redisTemplate.getExpire(remainingKey(dropId), TimeUnit.SECONDS)
        if (remainingTtlSeconds > 0) {
            redisTemplate.expire(key, remainingTtlSeconds, TimeUnit.SECONDS)
        }
    }

    /** Lua 소진 판정 통과(Admitted) 후 완충 permit을 시도한다. 실패 시 Redis 복원 + Throttled. */
    private fun admitWithSemaphore(
        dropId: Long,
        userId: Long,
        quantity: Int,
        idempotencyKey: String,
    ): ReservationResult {
        val acquired = admissionSemaphore.tryAcquire(acquireTimeoutMillis, TimeUnit.MILLISECONDS)
        if (!acquired) {
            executeCancelScript(dropId, userId, quantity, idempotencyKey)
            return ReservationResult.Throttled
        }
        return ReservationResult.Admitted
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
            ),
        ) { "reserve.lua 실행 결과가 null (dropId=$dropId)" }
    }

    private fun executeCancelScript(dropId: Long, userId: Long, quantity: Int, idempotencyKey: String) {
        val keys = listOf(remainingKey(dropId), buyerKey(dropId, userId), reservedKey(dropId, idempotencyKey))
        redisTemplate.execute(cancelScript, keys, quantity.toString())
    }

    private fun remainingKey(dropId: Long) = "goods:limited-drop:$dropId:remaining"

    private fun buyerKey(dropId: Long, userId: Long) = "goods:limited-drop:$dropId:buyer:$userId"

    private fun reservedKey(dropId: Long, idempotencyKey: String) = "goods:limited-drop:$dropId:reserved:$idempotencyKey"

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
    }
}
