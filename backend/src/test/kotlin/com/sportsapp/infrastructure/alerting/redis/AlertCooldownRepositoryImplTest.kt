package com.sportsapp.infrastructure.alerting.redis

import com.sportsapp.SharedTestContainers
import com.sportsapp.domain.alerting.vo.AlertSeverity
import com.sportsapp.domain.alerting.vo.AlertSignal
import com.sportsapp.domain.alerting.vo.AlertSource
import io.kotest.core.spec.style.BehaviorSpec
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
 * BE-04: `AlertCooldownRepositoryImpl` Redis 슬라이스 통합 검증.
 *
 * 다른 alerting infrastructure 구현체가 아직 없어 전체 애플리케이션 컨텍스트가 뜨지 않으므로
 * `RedisDistributedLockConcurrencyTest`와 동일하게 최소 SpringBootTest(TestApp)로 StringRedisTemplate만
 * TestContainers Redis에 연결해 검증한다.
 */
@SpringBootTest(classes = [AlertCooldownRepositoryImplTest.TestApp::class])
@ContextConfiguration(initializers = [AlertCooldownRepositoryImplTest.RedisInitializer::class])
@TestPropertySource(
    properties = [
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration," +
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
    ],
)
class AlertCooldownRepositoryImplTest @Autowired constructor(
    private val stringRedisTemplate: StringRedisTemplate,
) : BehaviorSpec({

    val repository = AlertCooldownRepositoryImpl(stringRedisTemplate, env = "test")
    val cooldown = Duration.ofMinutes(15)

    fun uniqueSignal(endpoint: String): AlertSignal =
        AlertSignal(endpoint, AlertSource.LATENCY, AlertSeverity.WARN)

    Given("쿨다운 상태가 없는 알람 신호") {
        When("최초로 tryAcquire를 호출하면") {
            val signal = uniqueSignal("/first-acquire")

            Then("true를 반환한다") {
                repository.tryAcquire(signal, cooldown) shouldBe true
            }
        }

        When("15분 쿨다운 내에 동일 신호로 재호출하면") {
            val signal = uniqueSignal("/repeat-within-cooldown")
            repository.tryAcquire(signal, cooldown)

            Then("false를 반환한다") {
                repository.tryAcquire(signal, cooldown) shouldBe false
            }
        }

        When("endpoint·source·severity 중 하나라도 다른 신호로 호출하면") {
            val original = AlertSignal("/independent", AlertSource.LATENCY, AlertSeverity.WARN)
            val differentSeverity = AlertSignal("/independent", AlertSource.LATENCY, AlertSeverity.CRITICAL)
            repository.tryAcquire(original, cooldown)

            Then("독립적으로 true를 반환한다") {
                repository.tryAcquire(differentSeverity, cooldown) shouldBe true
            }
        }

        When("TTL이 자연 만료된 뒤 동일 신호로 재호출하면") {
            val signal = uniqueSignal("/ttl-expiry")
            val shortCooldown = Duration.ofMillis(300)
            repository.tryAcquire(signal, shortCooldown)
            Thread.sleep(400)

            Then("true를 반환한다") {
                repository.tryAcquire(signal, cooldown) shouldBe true
            }
        }

        When("동일 신호에 50개 스레드가 동시에 tryAcquire를 호출하면") {
            val signal = uniqueSignal("/concurrent-acquire")
            val executor = Executors.newFixedThreadPool(THREAD_COUNT)
            val successCount = AtomicInteger(0)

            val tasks = (1..THREAD_COUNT).map {
                executor.submit<Boolean> {
                    val acquired = repository.tryAcquire(signal, cooldown)
                    if (acquired) successCount.incrementAndGet()
                    acquired
                }
            }
            tasks.forEach { it.get(WAIT_SECONDS, TimeUnit.SECONDS) }
            executor.shutdown()

            Then("정확히 1건만 true를 받는다") {
                successCount.get() shouldBe 1
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
        const val THREAD_COUNT = 50
        const val WAIT_SECONDS = 10L

        init {
            SharedTestContainers.redis
        }
    }
}
