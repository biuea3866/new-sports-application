package com.sportsapp.infrastructure.lock

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import com.sportsapp.SportsTestContainers

/**
 * R-01 + R-03: Redis Testcontainers 기반 동시성 + Lua compare-and-del 통합 검증.
 *
 * 100 스레드가 동일 키 락 시도 → 정확히 1건만 true 반환.
 * 보유자 일치 시에만 unlock 성공.
 */
@SpringBootTest(classes = [RedisDistributedLockConcurrencyTest.TestApp::class])
class RedisDistributedLockConcurrencyTest @Autowired constructor(
    private val redisTemplate: StringRedisTemplate,
) : BehaviorSpec({

    val lock = RedisDistributedLock(redisTemplate)

    Given("Redis 컨테이너 + RedisDistributedLock") {
        When("[R-01] 동일 키에 100 스레드 동시 tryLock") {
            val key = "concurrent:lock:test"
            redisTemplate.delete(key)

            val executor = Executors.newFixedThreadPool(THREAD_COUNT)
            val successCount = AtomicInteger(0)

            val tasks = (1..THREAD_COUNT).map { i ->
                executor.submit<Boolean> {
                    val acquired = lock.tryLock(key, "owner-$i", Duration.ofSeconds(30))
                    if (acquired) {
                        successCount.incrementAndGet()
                    }
                    acquired
                }
            }
            tasks.forEach { it.get(WAIT_SECONDS, TimeUnit.SECONDS) }
            executor.shutdown()

            Then("정확히 1건만 성공한다") {
                successCount.get() shouldBe 1
            }
        }

        When("[R-03] Lua compare-and-del 은 보유자 일치 시에만 키를 삭제한다") {
            val key = "lua:cad:test"
            redisTemplate.delete(key)

            lock.tryLock(key, "owner-a", Duration.ofSeconds(30)) shouldBe true

            Then("다른 보유자의 unlock 시도는 false 이고 키는 보존된다") {
                lock.unlock(key, "owner-b") shouldBe false
                redisTemplate.opsForValue().get(key) shouldBe "owner-a"
            }

            Then("정확한 보유자의 unlock 시도는 true 이고 키가 삭제된다") {
                lock.unlock(key, "owner-a") shouldBe true
                redisTemplate.opsForValue().get(key) shouldBe null
            }
        }
    }
}) {
    @SpringBootApplication
    class TestApp

    companion object {
        const val THREAD_COUNT = 100
        const val WAIT_SECONDS = 10L

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
