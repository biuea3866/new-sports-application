package com.sportsapp.infrastructure.lock

import com.sportsapp.domain.common.exceptions.RedisLockException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.RedisSystemException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.script.DefaultRedisScript

/**
 * [S-01] Redis 인프라 장애 시 RedisLockException 전파.
 *
 * `RedisConnectionFailureException` / `RedisSystemException` 같은 `DataAccessException` 하위는
 * `RedisLockException`(INFRA-07 BusinessException) 으로 변환되어 비즈니스 호출에 전파된다.
 */
class RedisDistributedLockFailureTest : BehaviorSpec({

    Given("Redis 가 연결 실패 상태") {
        val redisTemplate = mockk<StringRedisTemplate>()
        val valueOps = mockk<ValueOperations<String, String>>()
        every { redisTemplate.opsForValue() } returns valueOps
        every {
            valueOps.setIfAbsent("k", "v", Duration.ofSeconds(10))
        } throws RedisConnectionFailureException("Cannot get Jedis connection")

        val lock = RedisDistributedLock(redisTemplate)

        When("[S-01] tryLock 호출") {
            Then("RedisLockException 으로 wrap 되어 전파된다") {
                shouldThrow<RedisLockException> {
                    lock.tryLock("k", "v", Duration.ofSeconds(10))
                }
            }
        }
    }

    Given("Redis 가 시스템 예외 상태 (클러스터 타임아웃 등)") {
        val redisTemplate = mockk<StringRedisTemplate>()
        every {
            redisTemplate.execute(any<DefaultRedisScript<Long>>(), listOf("k"), "v")
        } throws RedisSystemException("Cluster topology error", RuntimeException())

        val lock = RedisDistributedLock(redisTemplate)

        When("[S-01b] unlock 호출") {
            Then("RedisLockException 으로 wrap 되어 전파된다") {
                shouldThrow<RedisLockException> {
                    lock.unlock("k", "v")
                }
            }
        }
    }
})
