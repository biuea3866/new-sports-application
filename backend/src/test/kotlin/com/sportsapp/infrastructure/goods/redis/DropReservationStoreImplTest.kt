package com.sportsapp.infrastructure.goods.redis

import com.sportsapp.SharedTestContainers
import com.sportsapp.domain.goods.gateway.RejectCounts
import com.sportsapp.domain.goods.gateway.RejectKind
import com.sportsapp.domain.goods.gateway.ReservationResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.dao.DataAccessException
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

    fun buildStore(
        semaphorePermits: Int = 1000,
        acquireTimeoutMillis: Long = 200,
        meterRegistry: SimpleMeterRegistry = SimpleMeterRegistry(),
    ) = DropReservationStoreImpl(
        redisTemplate = redisTemplate,
        meterRegistry = meterRegistry,
        semaphorePermits = semaphorePermits,
        acquireTimeoutMillis = acquireTimeoutMillis,
        markerTtlSeconds = 600,
    )

    fun cleanupDrop(dropId: Long) {
        redisTemplate.delete(
            listOf(
                "goods:limited-drop:$dropId:remaining",
                "goods:limited-drop:$dropId:reject:sold-out",
                "goods:limited-drop:$dropId:reject:too-early",
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

    Given("sold-out·too-early 거부가 기록되지 않은 회차") {
        val dropId = 1008L
        cleanupDrop(dropId)
        val store = buildStore()

        When("rejectCounts를 조회하면") {
            val result = store.rejectCounts(dropId)

            Then("두 카운트 모두 0이다") {
                result shouldBe RejectCounts(soldOutCount = 0, tooEarlyCount = 0)
            }
        }
    }

    Given("sold-out 거부 2건, too-early 거부 3건이 기록된 회차") {
        val dropId = 1009L
        cleanupDrop(dropId)
        val store = buildStore()

        When("recordReject를 각각 호출한 뒤 rejectCounts를 조회하면") {
            repeat(2) { store.recordReject(dropId, RejectKind.SOLD_OUT) }
            repeat(3) { store.recordReject(dropId, RejectKind.TOO_EARLY) }
            val result = store.rejectCounts(dropId)

            Then("각 사유별로 정확히 집계된다") {
                result shouldBe RejectCounts(soldOutCount = 2, tooEarlyCount = 3)
            }
        }
    }

    Given("remaining 키에 TTL이 설정된 회차") {
        val dropId = 1010L
        cleanupDrop(dropId)
        val store = buildStore()
        store.seedIfAbsent(dropId, 10, Duration.ofMinutes(10))

        When("recordReject를 호출하면") {
            store.recordReject(dropId, RejectKind.SOLD_OUT)

            Then("거부 카운터 키의 TTL이 remaining 키와 동일하게 정렬된다") {
                val rejectTtl = redisTemplate.getExpire("goods:limited-drop:$dropId:reject:sold-out", TimeUnit.SECONDS)
                rejectTtl shouldBeGreaterThan 0L
                rejectTtl shouldBeLessThanOrEqual 600L
            }
        }
    }

    Given("재고가 이미 소진된 회차") {
        val dropId = 1011L
        cleanupDrop(dropId)
        val meterRegistry = SimpleMeterRegistry()
        val store = buildStore(meterRegistry = meterRegistry)
        store.seedIfAbsent(dropId, 0, Duration.ofMinutes(10))

        When("reserve를 호출해 SoldOut이 반환되면") {
            val result = store.reserve(dropId, 1L, 1, 1, "idem-sold-out-metric")

            Then("sold_out 태그의 거부 지표가 증가한다") {
                result shouldBe ReservationResult.SoldOut
                meterRegistry.counter("limited_drop.reject", "kind", "sold_out").count() shouldBe 1.0
            }
        }
    }

    Given("perUserLimit을 초과하는 회차") {
        val dropId = 1012L
        val userId = 1L
        cleanupDrop(dropId)
        val meterRegistry = SimpleMeterRegistry()
        val store = buildStore(meterRegistry = meterRegistry)
        store.seedIfAbsent(dropId, 10, Duration.ofMinutes(10))
        store.reserve(dropId, userId, 1, 1, "idem-first") shouldBe ReservationResult.Admitted

        When("한도를 초과해 reserve를 호출하면") {
            val result = store.reserve(dropId, userId, 1, 1, "idem-second")

            Then("per_user 태그의 거부 지표가 증가한다") {
                result shouldBe ReservationResult.PerUserLimitExceeded(1)
                meterRegistry.counter("limited_drop.reject", "kind", "per_user").count() shouldBe 1.0
            }
        }
    }

    Given("완충 세마포어 permit이 모두 소진된 회차") {
        val dropId = 1013L
        cleanupDrop(dropId)
        val meterRegistry = SimpleMeterRegistry()
        val store = buildStore(semaphorePermits = 1, acquireTimeoutMillis = 100, meterRegistry = meterRegistry)
        store.seedIfAbsent(dropId, 10, Duration.ofMinutes(10))
        store.reserve(dropId, 1L, 1, 10, "idem-holder-metric") shouldBe ReservationResult.Admitted

        When("permit을 반납하지 않은 채 추가로 reserve를 호출하면") {
            val result = store.reserve(dropId, 2L, 1, 10, "idem-throttled-metric")

            Then("throttled 태그의 거부 지표가 증가한다") {
                result shouldBe ReservationResult.Throttled
                meterRegistry.counter("limited_drop.reject", "kind", "throttled").count() shouldBe 1.0
            }
        }
    }

    Given("buyer 키가 손상되어 Redis가 WRONGTYPE 오류를 반환하는 상황") {
        val dropId = 1014L
        val userId = 1L
        cleanupDrop(dropId)
        val meterRegistry = SimpleMeterRegistry()
        val store = buildStore(meterRegistry = meterRegistry)
        store.seedIfAbsent(dropId, 10, Duration.ofMinutes(10))
        redisTemplate.opsForList().leftPush("goods:limited-drop:$dropId:buyer:$userId", "corrupted")

        When("reserve를 호출하면") {
            Then("DataAccessException을 전파하고 redis-degraded 카운터가 증가한다") {
                shouldThrow<DataAccessException> {
                    store.reserve(dropId, userId, 1, 10, "idem-redis-degraded")
                }
                meterRegistry.counter("limited_drop.redis_degraded").count() shouldBe 1.0
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
