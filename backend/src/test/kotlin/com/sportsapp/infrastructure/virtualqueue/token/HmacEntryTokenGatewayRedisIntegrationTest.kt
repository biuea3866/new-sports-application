package com.sportsapp.infrastructure.virtualqueue.token

import com.sportsapp.SharedTestContainers
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import com.sportsapp.domain.virtualqueue.vo.QueueTargetType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.TimeUnit
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
 * `HmacEntryTokenGateway.issueIfAbsent` 통합 검증 — 실 Redis(Testcontainers), 멱등 마커(`SET NX EX 300`).
 * `DropReservationStoreImplTest`의 경량 SpringBootTest(Redis 전용) 패턴을 따른다.
 */
@SpringBootTest(classes = [HmacEntryTokenGatewayRedisIntegrationTest.TestApp::class])
@ContextConfiguration(initializers = [HmacEntryTokenGatewayRedisIntegrationTest.RedisInitializer::class])
@TestPropertySource(
    properties = [
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration," +
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
    ],
)
class HmacEntryTokenGatewayRedisIntegrationTest @Autowired constructor(
    private val redisTemplate: StringRedisTemplate,
) : BehaviorSpec({

    fun buildGateway(ttlSeconds: Long = 300L) =
        HmacEntryTokenGateway(
            redisTemplate = redisTemplate,
            secret = "test-virtual-queue-hmac-secret-value",
            ttlSeconds = ttlSeconds,
        )

    fun cleanup(target: QueueTarget, userId: Long) {
        redisTemplate.delete(target.tokenKey(userId))
    }

    Given("targetId=6001, userId=9001에 최초 issueIfAbsent를 호출하면") {
        val target = QueueTarget(type = QueueTargetType.LIMITED_DROP, targetId = 6001L)
        val userId = 9001L
        cleanup(target, userId)
        val gateway = buildGateway()

        When("발급된 토큰으로 verify를 호출하면") {
            val issued = gateway.issueIfAbsent(target, userId)
            val verified = gateway.verify(target.type.slug, target.targetId, userId, issued.raw)

            Then("서명·target·userId가 일치해 통과한다") {
                verified shouldBe true
            }
        }

        Then("멱등 마커 키에 TTL 300초 이하로 설정된다") {
            val ttl = redisTemplate.getExpire(target.tokenKey(userId), TimeUnit.SECONDS)
            (ttl in 1..300L) shouldBe true
        }
    }

    Given("동일 (target, userId)로 issueIfAbsent를 재호출하면") {
        val target = QueueTarget(type = QueueTargetType.LIMITED_DROP, targetId = 6002L)
        val userId = 9002L
        cleanup(target, userId)
        val gateway = buildGateway()
        val firstToken = gateway.issueIfAbsent(target, userId)

        When("두 번째 issueIfAbsent를 호출하면") {
            val secondToken = gateway.issueIfAbsent(target, userId)

            Then("SET NX 멱등으로 최초 발급 토큰을 그대로 반환한다") {
                secondToken.raw shouldBe firstToken.raw
            }
        }
    }

    Given("서로 다른 userId로 issueIfAbsent를 호출하면") {
        val target = QueueTarget(type = QueueTargetType.TICKETING_EVENT, targetId = 6003L)
        cleanup(target, 9003L)
        cleanup(target, 9004L)
        val gateway = buildGateway()

        When("각각 발급하면") {
            val tokenA = gateway.issueIfAbsent(target, 9003L)
            val tokenB = gateway.issueIfAbsent(target, 9004L)

            Then("서로 다른 토큰이 발급되고 각자 자신의 userId로만 검증을 통과한다") {
                tokenA.raw shouldBe tokenA.raw
                gateway.verify(target.type.slug, target.targetId, 9003L, tokenA.raw) shouldBe true
                gateway.verify(target.type.slug, target.targetId, 9004L, tokenA.raw) shouldBe false
                gateway.verify(target.type.slug, target.targetId, 9004L, tokenB.raw) shouldBe true
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
