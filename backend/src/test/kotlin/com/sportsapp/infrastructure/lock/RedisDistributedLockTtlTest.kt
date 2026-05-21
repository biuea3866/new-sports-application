package com.sportsapp.infrastructure.lock

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Duration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import com.sportsapp.SportsTestContainers

/**
 * [R-02] TTL 자동 해제 — TTL 만료 후 다음 tryLock 이 성공한다.
 */
@SpringBootTest(classes = [RedisDistributedLockTtlTest.TestApp::class])
class RedisDistributedLockTtlTest @Autowired constructor(
    private val redisTemplate: StringRedisTemplate,
) : BehaviorSpec({

    val lock = RedisDistributedLock(redisTemplate)

    Given("TTL 1초 짜리 락 보유") {
        val key = "ttl:expire:test"
        redisTemplate.delete(key)
        lock.tryLock(key, "owner-1", Duration.ofSeconds(1)) shouldBe true

        When("[R-02] TTL 만료 대기 후 다른 소유자가 tryLock 시도") {
            Thread.sleep(WAIT_AFTER_EXPIRE_MS)

            Then("새로운 소유자가 락을 획득한다") {
                lock.tryLock(key, "owner-2", Duration.ofSeconds(5)) shouldBe true
                redisTemplate.opsForValue().get(key) shouldBe "owner-2"
            }
        }
    }
}) {
    @SpringBootApplication
    class TestApp

    companion object {
        const val WAIT_AFTER_EXPIRE_MS = 1500L

        @JvmStatic
        val redis: GenericContainer<*> = SportsTestContainers.redis

        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(SportsTestContainers.REDIS_PORT) }
        }
    }
}
