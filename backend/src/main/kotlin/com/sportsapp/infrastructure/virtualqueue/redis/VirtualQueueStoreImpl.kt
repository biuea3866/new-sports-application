package com.sportsapp.infrastructure.virtualqueue.redis

import com.sportsapp.domain.virtualqueue.gateway.VirtualQueueStore
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import io.micrometer.core.instrument.MeterRegistry
import java.time.Duration
import java.time.ZonedDateTime
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

/**
 * `VirtualQueueStore` Redis 구현 — enter/admit/evict Lua 실행 + ZSET 순번 조회 + heartbeat +
 * `queue:active` 인덱스 관리.
 *
 * 키·TTL·Lua 계약: `backend/docs/redis/virtual-queue-keys.md`(SSOT), `20260709-redis-contract.md`.
 * `DropReservationStoreImpl`·`RedisDistributedLock`의 `StringRedisTemplate` + `DefaultRedisScript`
 * (`ClassPathResource` 로딩) 패턴을 그대로 따른다.
 *
 * **touchHeartbeat 불변식(인프라 리뷰 IW1)**: heartbeat ZSET뿐 아니라 waiting ZSET의 TTL도 함께
 * 슬라이딩 갱신한다. 폴링 사용자는 `enter`를 재호출하지 않으므로, 여기서 waiting을 갱신하지 않으면
 * 신규 진입 없이 폴링만 지속하는 대상의 waiting 키가 30분 뒤 만료되어 대량 무의도 소멸이 발생한다.
 *
 * **touchHeartbeat 불변식(코드 리뷰 p1 — seq 키 재발)**: waiting·heartbeat뿐 아니라 seq 키의 TTL도
 * 함께 슬라이딩 갱신한다. seq를 살리는 유일 경로가 `enter.lua`뿐이면, 판매 시작 후 신규 진입이
 * 멈추고 폴링만 지속되는 드레인 국면(예: 10만÷50TPS≈33분 > 30분 sliding TTL)에서 seq가 만료된다.
 * 그 순간 `admit.lua`의 `GET seq or '0'` → 0 → `admitted_count = min(count+batch, 0) = 0`으로
 * 고수위가 0으로 리셋되어 admission이 붕괴한다(redis-contract §1 seq 행 — TTL은 waiting과 동일해야
 * 하므로 갱신 트리거도 waiting과 동일하게 touchHeartbeat에서 함께 슬라이딩한다).
 *
 * Redis 인프라 장애(`DataAccessException`)는 여기서 삼키지 않고 그대로 전파한다 — 호출부
 * (`VirtualQueueDomainService`/`AdmissionDomainService`)가 fail-open 폴백을 판단한다. 이 지점에서는
 * [REDIS_DEGRADED_COUNTER] 카운터만 증가시킨다.
 */
@Component
class VirtualQueueStoreImpl(
    private val redisTemplate: StringRedisTemplate,
    private val meterRegistry: MeterRegistry,
    @Value("\${virtual-queue.sliding-ttl-seconds:1800}")
    private val slidingTtlSeconds: Long,
) : VirtualQueueStore {

    private val slidingTtl: Duration get() = Duration.ofSeconds(slidingTtlSeconds)

    private val enterScript: DefaultRedisScript<Long> = DefaultRedisScript(
        loadScript("redis/enter.lua"),
        Long::class.java,
    )

    private val admitScript: DefaultRedisScript<Long> = DefaultRedisScript(
        loadScript("redis/admit.lua"),
        Long::class.java,
    )

    private val evictScript: DefaultRedisScript<Long> = DefaultRedisScript(
        loadScript("redis/evict.lua"),
        Long::class.java,
    )

    override fun enterIfAbsent(target: QueueTarget, userId: Long, maxCapacity: Int): Long? = executeTracked {
        val seq = requireNotNull(
            redisTemplate.execute(
                enterScript,
                listOf(target.waitingKey(), target.heartbeatKey(), target.seqKey()),
                userId.toString(),
                nowEpochMs().toString(),
                maxCapacity.toString(),
                slidingTtl.toMillis().toString(),
            ),
        ) { "enter.lua 실행 결과가 null (target=$target, userId=$userId)" }
        if (seq < 0) null else seq
    }

    override fun rankOf(target: QueueTarget, userId: Long): Int? = executeTracked {
        redisTemplate.opsForZSet().rank(target.waitingKey(), userId.toString())?.toInt()
    }

    override fun seqOf(target: QueueTarget, userId: Long): Long? = executeTracked {
        redisTemplate.opsForZSet().score(target.waitingKey(), userId.toString())?.toLong()
    }

    override fun waitingSize(target: QueueTarget): Long = executeTracked {
        redisTemplate.opsForZSet().zCard(target.waitingKey()) ?: 0L
    }

    override fun admittedCount(target: QueueTarget): Long = executeTracked {
        redisTemplate.opsForValue().get(target.admittedCountKey())?.toLongOrNull() ?: 0L
    }

    override fun touchHeartbeat(target: QueueTarget, userId: Long): Unit = executeTracked {
        redisTemplate.opsForZSet().add(target.heartbeatKey(), userId.toString(), nowEpochMs().toDouble())
        redisTemplate.expire(target.waitingKey(), slidingTtl)
        redisTemplate.expire(target.heartbeatKey(), slidingTtl)
        redisTemplate.expire(target.seqKey(), slidingTtl)
        Unit
    }

    override fun leave(target: QueueTarget, userId: Long): Unit = executeTracked {
        redisTemplate.opsForZSet().remove(target.waitingKey(), userId.toString())
        redisTemplate.opsForZSet().remove(target.heartbeatKey(), userId.toString())
        redisTemplate.delete(target.tokenKey(userId))
        Unit
    }

    override fun advanceAdmission(target: QueueTarget, batchSize: Int): Long = executeTracked {
        requireNotNull(
            redisTemplate.execute(
                admitScript,
                listOf(target.admittedCountKey(), target.seqKey()),
                batchSize.toString(),
                slidingTtl.toMillis().toString(),
            ),
        ) { "admit.lua 실행 결과가 null (target=$target)" }
    }

    override fun sweepStale(target: QueueTarget, staleBeforeEpochMs: Long, maxEvictPerTick: Int): Int = executeTracked {
        requireNotNull(
            redisTemplate.execute(
                evictScript,
                listOf(target.waitingKey(), target.heartbeatKey()),
                staleBeforeEpochMs.toString(),
                maxEvictPerTick.toString(),
            ),
        ) { "evict.lua 실행 결과가 null (target=$target)" }.toInt()
    }

    override fun activeTargets(): Set<QueueTarget> = executeTracked {
        redisTemplate.opsForSet().members(QueueTarget.ACTIVE_TARGETS_KEY)
            ?.map { QueueTarget.fromActiveMember(it) }
            ?.toSet()
            ?: emptySet()
    }

    override fun registerActive(target: QueueTarget): Unit = executeTracked {
        redisTemplate.opsForSet().add(QueueTarget.ACTIVE_TARGETS_KEY, target.activeMember())
        Unit
    }

    override fun seqExists(target: QueueTarget): Boolean = executeTracked {
        redisTemplate.hasKey(target.seqKey()) == true
    }

    override fun deactivate(target: QueueTarget): Unit = executeTracked {
        redisTemplate.opsForSet().remove(QueueTarget.ACTIVE_TARGETS_KEY, target.activeMember())
        Unit
    }

    /**
     * Redis 인프라 장애(`DataAccessException`)를 [REDIS_DEGRADED_COUNTER]로 관측하되, 예외 자체는
     * 삼키지 않고 그대로 재전파한다 — 호출부의 fail-open 판단을 이 레이어가 가로채지 않는다.
     */
    private fun <T> executeTracked(block: () -> T): T = try {
        block()
    } catch (exception: DataAccessException) {
        meterRegistry.counter(REDIS_DEGRADED_COUNTER).increment()
        throw exception
    }

    private fun nowEpochMs(): Long = ZonedDateTime.now().toInstant().toEpochMilli()

    private fun loadScript(classpath: String): String =
        ClassPathResource(classpath).inputStream.bufferedReader().use { it.readText() }

    companion object {
        private const val REDIS_DEGRADED_COUNTER = "virtual_queue.redis_degraded"
    }
}
