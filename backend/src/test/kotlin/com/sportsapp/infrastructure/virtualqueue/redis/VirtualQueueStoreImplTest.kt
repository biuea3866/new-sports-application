package com.sportsapp.infrastructure.virtualqueue.redis

import com.sportsapp.SharedTestContainers
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import com.sportsapp.domain.virtualqueue.vo.QueueTargetType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Duration
import java.util.concurrent.TimeUnit
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
 * `VirtualQueueStoreImpl` 통합 검증 — 실 Redis(Testcontainers), enter/admit/evict.lua 원자성 +
 * touchHeartbeat sliding TTL 갱신 불변식(인프라 리뷰 IW1).
 *
 * `DropReservationStoreImplTest`의 경량 SpringBootTest(Redis 전용) 패턴을 따른다.
 */
@SpringBootTest(classes = [VirtualQueueStoreImplTest.TestApp::class])
@ContextConfiguration(initializers = [VirtualQueueStoreImplTest.RedisInitializer::class])
@TestPropertySource(
    properties = [
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration," +
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
    ],
)
class VirtualQueueStoreImplTest @Autowired constructor(
    private val redisTemplate: StringRedisTemplate,
) : BehaviorSpec({

    fun buildStore(
        slidingTtlSeconds: Long = 1800,
        meterRegistry: SimpleMeterRegistry = SimpleMeterRegistry(),
    ) = VirtualQueueStoreImpl(
        redisTemplate = redisTemplate,
        meterRegistry = meterRegistry,
        slidingTtlSeconds = slidingTtlSeconds,
    )

    fun cleanup(target: QueueTarget, vararg userIds: Long) {
        redisTemplate.delete(
            listOf(target.waitingKey(), target.heartbeatKey(), target.seqKey(), target.admittedCountKey()),
        )
        userIds.forEach { redisTemplate.delete(target.tokenKey(it)) }
        redisTemplate.opsForSet().remove(QueueTarget.ACTIVE_TARGETS_KEY, target.activeMember())
    }

    Given("동일 userId로 enterIfAbsent를 2회 호출하면") {
        val target = QueueTarget(QueueTargetType.LIMITED_DROP, 3001L)
        cleanup(target, 900001L)
        val store = buildStore()

        When("최초 진입 후 재진입하면") {
            val firstSeq = store.enterIfAbsent(target, 900001L, maxCapacity = 100)
            val secondSeq = store.enterIfAbsent(target, 900001L, maxCapacity = 100)

            Then("두 번 모두 최초 seq(1)를 유지한다 (enter.lua 멱등)") {
                firstSeq shouldBe 1L
                secondSeq shouldBe 1L
            }
        }
    }

    Given("maxCapacity=3인 대상에 이미 3명이 진입한 상태에서") {
        val target = QueueTarget(QueueTargetType.LIMITED_DROP, 3002L)
        cleanup(target, 1L, 2L, 3L, 4L)
        val store = buildStore()
        store.enterIfAbsent(target, 1L, maxCapacity = 3)
        store.enterIfAbsent(target, 2L, maxCapacity = 3)
        store.enterIfAbsent(target, 3L, maxCapacity = 3)

        When("4번째 신규 사용자가 진입을 시도하면") {
            val result = store.enterIfAbsent(target, 4L, maxCapacity = 3)

            Then("null(포화)을 반환한다") {
                result.shouldBeNull()
            }
        }
    }

    Given("3명이 진입한 뒤 최초 진입자가 leave로 제거되면 (§0-1 판정/표시 분리)") {
        val target = QueueTarget(QueueTargetType.LIMITED_DROP, 3003L)
        cleanup(target, 910001L, 910002L, 910003L)
        val store = buildStore()
        store.enterIfAbsent(target, 910001L, maxCapacity = 100)
        store.enterIfAbsent(target, 910002L, maxCapacity = 100)
        store.enterIfAbsent(target, 910003L, maxCapacity = 100)
        store.leave(target, 910001L)

        When("남은 사용자(910002)의 seqOf와 rankOf를 조회하면") {
            val seq = store.seqOf(target, 910002L)
            val rank = store.rankOf(target, 910002L)

            Then("seqOf(고정 시퀀스)는 2로 불변이고, rankOf(ZRANK)는 0으로 전진한다") {
                seq shouldBe 2L
                rank shouldBe 0
            }
        }
    }

    Given("10명이 진입해 seq=10, admitted_count=0인 상태에서") {
        val target = QueueTarget(QueueTargetType.LIMITED_DROP, 3004L)
        cleanup(target, *(1L..10L).toList().toLongArray())
        val store = buildStore()
        (1..10L).forEach { userId -> store.enterIfAbsent(target, userId, maxCapacity = 100) }

        When("advanceAdmission을 batchSize=100으로 호출하면") {
            val result = store.advanceAdmission(target, batchSize = 100)

            Then("seq(=10) 상한으로 admitted_count가 제한된다") {
                result shouldBe 10L
                store.admittedCount(target) shouldBe 10L
            }
        }
    }

    Given("stale heartbeat 1건과 정상 heartbeat 1건이 섞인 대상에서") {
        val target = QueueTarget(QueueTargetType.LIMITED_DROP, 3005L)
        cleanup(target, 920001L, 920002L)
        val store = buildStore()
        store.enterIfAbsent(target, 920001L, maxCapacity = 100)
        redisTemplate.opsForZSet().add(target.heartbeatKey(), "920001", nowEpochMs() - 70_000.0) // 70초 전 → stale
        store.enterIfAbsent(target, 920002L, maxCapacity = 100)

        When("sweepStale을 cutoff=now-60s, maxEvictPerTick=50으로 호출하면") {
            val evicted = store.sweepStale(target, staleBeforeEpochMs = (nowEpochMs() - 60_000), maxEvictPerTick = 50)

            Then("stale 1건만 방출되고 정상 member는 남는다") {
                evicted shouldBe 1
                store.seqOf(target, 920001L).shouldBeNull()
                store.seqOf(target, 920002L).shouldNotBeNull()
            }
        }
    }

    Given("stale heartbeat 5건, maxEvictPerTick=2로 제한되면") {
        val target = QueueTarget(QueueTargetType.LIMITED_DROP, 3006L)
        cleanup(target, *(1L..5L).toList().toLongArray())
        val store = buildStore()
        (1..5L).forEach { userId ->
            store.enterIfAbsent(target, userId, maxCapacity = 100)
            redisTemplate.opsForZSet().add(target.heartbeatKey(), userId.toString(), nowEpochMs() - 70_000.0)
        }

        When("sweepStale을 maxEvictPerTick=2로 호출하면") {
            val evicted = store.sweepStale(target, staleBeforeEpochMs = (nowEpochMs() - 60_000), maxEvictPerTick = 2)

            Then("상한(2)만큼만 방출되고 나머지 3건은 남는다") {
                evicted shouldBe 2
                store.waitingSize(target) shouldBe 3L
            }
        }
    }

    Given("leave 대상 사용자가 waiting·heartbeat·token 키를 모두 보유한 상태에서") {
        val target = QueueTarget(QueueTargetType.LIMITED_DROP, 3007L)
        cleanup(target, 930001L)
        val store = buildStore()
        store.enterIfAbsent(target, 930001L, maxCapacity = 100)
        redisTemplate.opsForValue().set(target.tokenKey(930001L), "issued-token", Duration.ofMinutes(5))

        When("leave를 호출하면") {
            store.leave(target, 930001L)

            Then("waiting·heartbeat·token 키가 모두 정리된다") {
                store.seqOf(target, 930001L).shouldBeNull()
                store.rankOf(target, 930001L).shouldBeNull()
                redisTemplate.opsForValue().get(target.tokenKey(930001L)).shouldBeNull()
            }
        }
    }

    Given("waiting 키의 TTL이 곧 만료될 만큼 짧게 설정된 상태에서 (폴링만 지속하고 신규 enter가 없는 사용자)") {
        val target = QueueTarget(QueueTargetType.LIMITED_DROP, 3008L)
        cleanup(target, 940001L)
        val store = buildStore(slidingTtlSeconds = 1800)
        store.enterIfAbsent(target, 940001L, maxCapacity = 100)
        // 30분 대기 없이 재현하기 위해 waiting 키 TTL을 인위적으로 1초로 축소한다.
        redisTemplate.expire(target.waitingKey(), Duration.ofSeconds(1))

        When("touchHeartbeat을 호출하면") {
            store.touchHeartbeat(target, 940001L)

            Then("heartbeat뿐 아니라 waiting 키의 TTL도 sliding TTL(1800초)로 함께 갱신된다 (인프라 리뷰 IW1)") {
                val waitingTtl = redisTemplate.getExpire(target.waitingKey(), TimeUnit.SECONDS)
                val heartbeatTtl = redisTemplate.getExpire(target.heartbeatKey(), TimeUnit.SECONDS)
                waitingTtl shouldBeGreaterThan 1700L
                waitingTtl shouldBeLessThanOrEqual 1800L
                heartbeatTtl shouldBeGreaterThan 1700L
            }
        }
    }

    Given("seq 키의 TTL이 곧 만료될 만큼 짧게 설정된 상태에서 (판매 시작 후 신규 진입 없이 폴링만 지속하는 드레인 국면)") {
        val target = QueueTarget(QueueTargetType.LIMITED_DROP, 3011L)
        cleanup(target, 940002L)
        val store = buildStore(slidingTtlSeconds = 1800)
        store.enterIfAbsent(target, 940002L, maxCapacity = 100)
        // 드레인 국면(신규 enter 없음)을 재현하기 위해 seq 키 TTL을 인위적으로 1초로 축소한다.
        redisTemplate.expire(target.seqKey(), Duration.ofSeconds(1))

        When("touchHeartbeat을 반복 호출하면") {
            store.touchHeartbeat(target, 940002L)

            Then("seq 키의 TTL도 waiting·heartbeat과 동일하게 sliding TTL(1800초)로 갱신되어 만료되지 않는다") {
                val seqTtl = redisTemplate.getExpire(target.seqKey(), TimeUnit.SECONDS)
                seqTtl shouldBeGreaterThan 1700L
                seqTtl shouldBeLessThanOrEqual 1800L
            }
        }
    }

    Given("waiting 키가 손상되어 Redis가 WRONGTYPE 오류를 반환하는 상황") {
        val target = QueueTarget(QueueTargetType.LIMITED_DROP, 3009L)
        cleanup(target)
        val meterRegistry = SimpleMeterRegistry()
        val store = buildStore(meterRegistry = meterRegistry)
        redisTemplate.opsForList().leftPush(target.waitingKey(), "corrupted")

        When("enterIfAbsent를 호출하면") {
            Then("DataAccessException을 전파하고 virtual_queue.redis_degraded 카운터가 증가한다") {
                shouldThrow<DataAccessException> {
                    store.enterIfAbsent(target, 950001L, maxCapacity = 100)
                }
                meterRegistry.counter("virtual_queue.redis_degraded").count() shouldBe 1.0
            }
        }
    }

    Given("활성 대상이 등록되지 않은 상태에서") {
        val target = QueueTarget(QueueTargetType.TICKETING_EVENT, 3010L)
        cleanup(target)

        When("registerActive 호출 후 activeTargets를 조회하면") {
            val store = buildStore()
            store.registerActive(target)
            val actives = store.activeTargets()

            Then("등록한 대상이 포함된다") {
                actives shouldBe setOf(target)
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

private fun nowEpochMs(): Long = System.currentTimeMillis()
