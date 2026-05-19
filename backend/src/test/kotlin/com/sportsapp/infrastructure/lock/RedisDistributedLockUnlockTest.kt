package com.sportsapp.infrastructure.lock

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript

/**
 * U-02: `RedisDistributedLock.unlock` 의 compare-and-del 시맨틱 단위 검증 (MockK).
 *
 * 보유자가 일치하면 Redis 가 1 반환 → unlock 결과 true.
 * 불일치 시 Redis 가 0 반환 → unlock 결과 false (다른 사용자의 락은 삭제 안 됨).
 */
class RedisDistributedLockUnlockTest : BehaviorSpec({

    Given("RedisDistributedLock 인스턴스") {
        val redisTemplate = mockk<StringRedisTemplate>()
        val lock = RedisDistributedLock(redisTemplate)

        When("[U-02] 보유자가 일치하면 unlock 은 true 를 반환한다") {
            every {
                redisTemplate.execute(any<DefaultRedisScript<Long>>(), listOf("k1"), "owner-a")
            } returns 1L

            val result = lock.unlock("k1", "owner-a")

            Then("Redis 가 1 반환 → true") {
                result shouldBe true
            }
        }

        When("[U-02b] 보유자가 불일치하면 unlock 은 false 를 반환한다") {
            every {
                redisTemplate.execute(any<DefaultRedisScript<Long>>(), listOf("k2"), "owner-b")
            } returns 0L

            val result = lock.unlock("k2", "owner-b")

            Then("Redis 가 0 반환 → false (다른 사용자의 락 보존)") {
                result shouldBe false
            }
        }
    }
})
