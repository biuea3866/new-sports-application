package com.sportsapp.infrastructure.virtualqueue.redis

import com.sportsapp.SharedTestContainers
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.support.TestPropertySourceUtils

private fun loadLuaScript(classpath: String): String =
    ClassPathResource(classpath).inputStream.bufferedReader().use { it.readText() }

/**
 * BE-01 계약 검증 — `enter.lua`/`admit.lua`/`evict.lua`를 실 Redis(Testcontainers)에 그대로 로드해
 * 원자성·반환값을 검증한다.
 *
 * `VirtualQueueStoreImpl`(BE-02)이 이 스크립트들을 감싼 Store 추상화를 제공하기 전 단계라,
 * 여기서는 Lua 스크립트 자체의 계약(`20260709-redis-contract.md` §0-1/§0-2/§2)만 raw EVAL로
 * 검증한다. `DropReservationStoreImplTest`의 경량 SpringBootTest(Redis 전용) 패턴을 따른다.
 */
@SpringBootTest(classes = [VirtualQueueLuaScriptsContractTest.TestApp::class])
@ContextConfiguration(initializers = [VirtualQueueLuaScriptsContractTest.RedisInitializer::class])
@TestPropertySource(
    properties = [
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration," +
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
    ],
)
class VirtualQueueLuaScriptsContractTest @Autowired constructor(
    private val redisTemplate: StringRedisTemplate,
) : BehaviorSpec({

    val enterScript = DefaultRedisScript(loadLuaScript("redis/enter.lua"), Long::class.java)
    val admitScript = DefaultRedisScript(loadLuaScript("redis/admit.lua"), Long::class.java)
    val evictScript = DefaultRedisScript(loadLuaScript("redis/evict.lua"), Long::class.java)

    fun waitingKey(targetId: Long) = "queue:ld:$targetId:waiting"
    fun heartbeatKey(targetId: Long) = "queue:ld:$targetId:heartbeat"
    fun seqKey(targetId: Long) = "queue:ld:$targetId:seq"
    fun admittedCountKey(targetId: Long) = "queue:ld:$targetId:admitted_count"

    fun cleanup(targetId: Long) {
        redisTemplate.delete(
            listOf(waitingKey(targetId), heartbeatKey(targetId), seqKey(targetId), admittedCountKey(targetId)),
        )
    }

    fun enter(targetId: Long, userId: Long, maxCapacity: Int = 100, nowEpochMs: Long = 1_800_000_000_000L): Long? =
        redisTemplate.execute(
            enterScript,
            listOf(waitingKey(targetId), heartbeatKey(targetId), seqKey(targetId)),
            userId.toString(),
            nowEpochMs.toString(),
            maxCapacity.toString(),
            "1800000",
        )

    Given("targetId=2001에 신규 사용자 2명이 순서대로 진입하면") {
        val targetId = 2001L
        cleanup(targetId)

        When("enter.lua를 각각 호출하면") {
            val firstSeq = enter(targetId, userId = 900001L)
            val secondSeq = enter(targetId, userId = 900002L)

            Then("INCR seq로 1, 2 순서로 채번된다") {
                firstSeq shouldBe 1L
                secondSeq shouldBe 2L
            }
        }
    }

    Given("이미 진입한 사용자가 재진입하면") {
        val targetId = 2002L
        val userId = 900010L
        cleanup(targetId)
        enter(targetId, userId)

        When("동일 userId로 enter.lua를 다시 호출하면") {
            val secondSeq = enter(targetId, userId, nowEpochMs = 1_800_000_005_000L)

            Then("기존 seq(1)를 그대로 반환한다 — 멱등, 재채번 없음") {
                secondSeq shouldBe 1L
            }
        }
    }

    Given("maxCapacity=3인 대상에 이미 3명이 진입한 상태에서") {
        val targetId = 2003L
        cleanup(targetId)
        enter(targetId, 1L, maxCapacity = 3)
        enter(targetId, 2L, maxCapacity = 3)
        enter(targetId, 3L, maxCapacity = 3)

        When("4번째 신규 사용자가 enter.lua를 호출하면") {
            val result = enter(targetId, 4L, maxCapacity = 3)

            Then("-1(포화)을 반환한다") {
                result shouldBe -1L
            }
        }
    }

    Given("10명이 진입해 seq=10, admitted_count=0인 상태에서") {
        val targetId = 2004L
        cleanup(targetId)
        (1..10L).forEach { userId -> enter(targetId, userId) }

        When("admit.lua를 batchSize=100으로 호출하면") {
            val result = redisTemplate.execute(
                admitScript,
                listOf(admittedCountKey(targetId), seqKey(targetId)),
                "100",
                "1800000",
            )

            Then("seq(=10) 상한으로 제한된다 — seenTotal 원천은 seq, ZCARD가 아니다") {
                result shouldBe 10L
            }
        }
    }

    Given("waiting/heartbeat에 stale member 1건과 정상 member 1건이 섞여 있으면") {
        val targetId = 2005L
        cleanup(targetId)
        enter(targetId, 700001L, nowEpochMs = 1_799_999_929_000L) // 70초 전 heartbeat → stale
        enter(targetId, 700002L, nowEpochMs = 1_799_999_999_000L) // 정상 heartbeat

        When("evict.lua를 cutoff=now-60s, maxEvictPerTick=50으로 호출하면") {
            val evicted = redisTemplate.execute(
                evictScript,
                listOf(waitingKey(targetId), heartbeatKey(targetId)),
                "1799999939000",
                "50",
            )

            Then("stale 1건만 방출되고 정상 member는 남는다") {
                evicted shouldBe 1L
                redisTemplate.opsForZSet().score(waitingKey(targetId), "700001").shouldBeNull()
                redisTemplate.opsForZSet().score(heartbeatKey(targetId), "700001").shouldBeNull()
                redisTemplate.opsForZSet().score(waitingKey(targetId), "700002").shouldNotBeNull()
            }
        }
    }

    Given("stale member가 5건, maxEvictPerTick=2로 제한되면") {
        val targetId = 2006L
        cleanup(targetId)
        (1..5L).forEach { userId -> enter(targetId, userId, nowEpochMs = 1_799_999_929_000L) }

        When("evict.lua를 maxEvictPerTick=2로 호출하면") {
            val evicted = redisTemplate.execute(
                evictScript,
                listOf(waitingKey(targetId), heartbeatKey(targetId)),
                "1799999939000",
                "2",
            )

            Then("상한(2)만큼만 방출되고 나머지는 다음 틱으로 남는다") {
                evicted shouldBe 2L
                redisTemplate.opsForZSet().zCard(waitingKey(targetId)) shouldBe 3L
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
