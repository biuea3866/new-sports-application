package com.sportsapp.infrastructure.lock

import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.exceptions.RedisLockException
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component

/**
 * Redis 기반 분산 락 구현.
 *
 * - `tryLock`: `SET key value NX EX ttl` — 원자적으로 set if not exists + TTL.
 * - `unlock`: Lua compare-and-del — 소유자가 일치할 때만 삭제. 다른 사용자의 락을 실수로 해제 못 함.
 *
 * Redis 인프라 장애(연결 실패 / 시스템 예외 / 클러스터 타임아웃) 는 `DataAccessException` 으로 올라오므로
 * 포괄 catch 후 `RedisLockException` 으로 wrap 하여 비즈니스 호출에 전파한다.
 */
@Component
class RedisDistributedLock(
    private val redisTemplate: StringRedisTemplate,
) : DistributedLock {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val unlockScript: DefaultRedisScript<Long> = DefaultRedisScript(
        UNLOCK_LUA,
        Long::class.java,
    )

    override fun tryLock(key: String, value: String, ttl: Duration): Boolean {
        return try {
            redisTemplate.opsForValue().setIfAbsent(key, value, ttl) == true
        } catch (e: DataAccessException) {
            logger.error("Redis tryLock 실패: key={}", key, e)
            throw RedisLockException("Redis 접근 실패 (tryLock key=$key)")
        }
    }

    override fun unlock(key: String, value: String): Boolean {
        return try {
            val result = redisTemplate.execute(unlockScript, listOf(key), value)
            result == 1L
        } catch (e: DataAccessException) {
            logger.error("Redis unlock 실패: key={}", key, e)
            throw RedisLockException("Redis 접근 실패 (unlock key=$key)")
        }
    }

    companion object {
        // 보유자(KEYS[1] 값 == ARGV[1]) 일치 시에만 키 삭제. compare-and-del 보장.
        private const val UNLOCK_LUA = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
        """
    }
}
