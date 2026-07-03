package com.sportsapp.infrastructure.goods.redis

import com.sportsapp.SharedTestContainers
import com.sportsapp.domain.goods.gateway.ReservationResult
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.support.TestPropertySourceUtils

/**
 * DropReservationStoreImpl 통합 검증 — 실 Redis(Testcontainers), reserve.lua/cancel.lua 원자성.
 * `RedisDistributedLockConcurrencyTest` 의 경량 SpringBootTest(Redis 전용) 패턴을 따른다.
 */
@SpringBootTest(classes = [DropReservationStoreImplTest.TestApp::class])
@ContextConfiguration(initializers = [DropReservationStoreImplTest.RedisInitializer::class])
@TestPropertySource(properties = [
    "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
        "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration," +
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
])
class DropReservationStoreImplTest @Autowired constructor(
    private val redisTemplate: StringRedisTemplate,
) : BehaviorSpec({

    fun buildStore(semaphorePermits: Int = 1000, acquireTimeoutMillis: Long = 200) =
        DropReservationStoreImpl(
            redisTemplate = redisTemplate,
            semaphorePermits = semaphorePermits,
            acquireTimeoutMillis = acquireTimeoutMillis,
            markerTtlSeconds = 600,
        )

    fun cleanupDrop(dropId: Long) {
        redisTemplate.delete(
            listOf(
                "goods:limited-drop:$dropId:remaining",
            ),
        )
        redisTemplate.keys("goods:limited-drop:$dropId:buyer:*").let { redisTemplate.delete(it) }
        redisTemplate.keys("goods:limited-drop:$dropId:reserved:*").let { redisTemplate.delete(it) }
    }

    Given("재고 100인 회차에 500명이 동시에 reserve를 요청하면") {
        val dropId = 1001L
        cleanupDrop(dropId)
        val store = buildStore(semaphorePermits = 1000)
        store.seedIfAbsent(dropId, 100, Duration.ofMinutes(10))

        When("동시에 500건을 실행하면") {
            val executor = Executors.newFixedThreadPool(64)
            val admittedCount = AtomicInteger(0)
            val soldOutCount = AtomicInteger(0)

            val tasks = (1..500).map { userId ->
                executor.submit {
                    val result = store.reserve(
                        dropId = dropId,
                        userId = userId.toLong(),
                        quantity = 1,
                        perUserLimit = 1,
                        idempotencyKey = "idem-$userId",
                    )
                    when (result) {
                        is ReservationResult.Admitted -> admittedCount.incrementAndGet()
                        is ReservationResult.SoldOut -> soldOutCount.incrementAndGet()
                        else -> Unit
                    }
                }
            }
            tasks.forEach { it.get(30, TimeUnit.SECONDS) }
            executor.shutdown()

            Then("정확히 100건은 Admitted, 400건은 SoldOut이다") {
                admittedCount.get() shouldBe 100
                soldOutCount.get() shouldBe 400
                store.remaining(dropId) shouldBe 0
            }
        }
    }

    Given("동일 idempotencyKey로 2회 reserve를 호출하면") {
        val dropId = 1002L
        val userId = 1L
        cleanupDrop(dropId)
        val store = buildStore()
        store.seedIfAbsent(dropId, 5, Duration.ofMinutes(10))

        When("같은 idempotencyKey로 두 번 요청하면") {
            val first = store.reserve(dropId, userId, 1, 3, "idem-dup")
            val second = store.reserve(dropId, userId, 1, 3, "idem-dup")

            Then("두 번째는 AlreadyReserved이고 remaining은 1회만 감소한다") {
                first shouldBe ReservationResult.Admitted
                second shouldBe ReservationResult.AlreadyReserved
                store.remaining(dropId) shouldBe 4
            }
        }
    }

    Given("perUserLimit=1인 회차에 같은 userId가 2건 요청하면") {
        val dropId = 1003L
        val userId = 1L
        cleanupDrop(dropId)
        val store = buildStore()
        store.seedIfAbsent(dropId, 10, Duration.ofMinutes(10))

        When("서로 다른 idempotencyKey로 두 번 요청하면") {
            val first = store.reserve(dropId, userId, 1, 1, "idem-a")
            val second = store.reserve(dropId, userId, 1, 1, "idem-b")

            Then("두 번째는 PerUserLimitExceeded다") {
                first shouldBe ReservationResult.Admitted
                second shouldBe ReservationResult.PerUserLimitExceeded(1)
                store.remaining(dropId) shouldBe 9
            }
        }
    }

    Given("Admitted된 예약을 cancel로 되돌리면") {
        val dropId = 1004L
        val userId = 1L
        cleanupDrop(dropId)
        val store = buildStore()
        store.seedIfAbsent(dropId, 5, Duration.ofMinutes(10))
        store.reserve(dropId, userId, 2, 5, "idem-cancel") shouldBe ReservationResult.Admitted

        When("cancel을 호출하면") {
            store.cancel(dropId, userId, 2, "idem-cancel")

            Then("remaining이 정확히 복원되고 동일 idempotencyKey로 재시도 가능하다") {
                store.remaining(dropId) shouldBe 5
                store.reserve(dropId, userId, 2, 5, "idem-cancel") shouldBe ReservationResult.Admitted
            }
        }
    }

    Given("이미 시드된 회차에 seedIfAbsent를 재호출하면") {
        val dropId = 1005L
        cleanupDrop(dropId)
        val store = buildStore()

        When("동일 dropId로 seedIfAbsent를 2회 호출하면") {
            store.seedIfAbsent(dropId, 20, Duration.ofMinutes(10))
            store.seedIfAbsent(dropId, 999, Duration.ofMinutes(10))

            Then("remaining은 최초 limitedQuantity(20)로 유지된다") {
                store.remaining(dropId) shouldBe 20
            }
        }
    }

    Given("완충 세마포어 permit이 모두 소진된 상태에서") {
        val dropId = 1006L
        cleanupDrop(dropId)
        val store = buildStore(semaphorePermits = 1, acquireTimeoutMillis = 100)
        store.seedIfAbsent(dropId, 10, Duration.ofMinutes(10))
        store.reserve(dropId, 1L, 1, 10, "idem-holder") shouldBe ReservationResult.Admitted

        When("permit을 반납하지 않은 채 추가로 reserve를 호출하면") {
            val throttled = store.reserve(dropId, 2L, 1, 10, "idem-throttled")

            Then("Admitted 대신 Throttled를 반환하고 Redis remaining을 복원한다") {
                throttled shouldBe ReservationResult.Throttled
                store.remaining(dropId) shouldBe 9
            }
        }
    }

    Given("시드되지 않은 회차의 remaining을 조회하면") {
        val dropId = 1007L
        cleanupDrop(dropId)
        val store = buildStore()

        When("remaining을 호출하면") {
            val result = store.remaining(dropId)

            Then("null을 반환한다") {
                result.shouldBeNull()
            }
        }
    }
}) {
    @SpringBootApplication
    class TestApp

    class RedisInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(applicationContext: ConfigurableApplicationContext) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                applicationContext,
                "spring.data.redis.host=${SharedTestContainers.redis.host}",
                "spring.data.redis.port=${SharedTestContainers.redis.getMappedPort(6379)}",
            )
        }
    }

    companion object {
        init {
            SharedTestContainers.redis
        }
    }
}
