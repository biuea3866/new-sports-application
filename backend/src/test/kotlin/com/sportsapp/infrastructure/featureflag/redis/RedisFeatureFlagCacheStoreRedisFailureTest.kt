package com.sportsapp.infrastructure.featureflag.redis

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * Redis 연결 실패 시 `RedisFeatureFlagCacheStore.get`이 예외를 전파하지 않고
 * null로 처리되며 `redis-degraded` 경보(WARN 로그)가 발신되는지 검증한다.
 *
 * 실제 Redis 클라이언트(Lettuce)가 도달 불가 포트에 연결을 시도해 진짜 `DataAccessException`을
 * 던지도록 구성한다(Mock으로 예외를 흉내내지 않는다).
 */
class RedisFeatureFlagCacheStoreRedisFailureTest : BehaviorSpec({

    Given("Redis에 연결할 수 없는 상태에서") {
        val clientConfiguration = LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofMillis(300))
            .build()
        val brokenConnectionFactory = LettuceConnectionFactory(
            RedisStandaloneConfiguration("127.0.0.1", UNREACHABLE_PORT),
            clientConfiguration,
        ).apply { afterPropertiesSet() }
        val stringRedisTemplate = StringRedisTemplate(brokenConnectionFactory).apply { afterPropertiesSet() }
        val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())
        val meterRegistry = SimpleMeterRegistry()
        val cacheStore = RedisFeatureFlagCacheStore(stringRedisTemplate, objectMapper, meterRegistry)

        val listAppender = ListAppender<ILoggingEvent>().apply { start() }
        val logger = LoggerFactory.getLogger(RedisFeatureFlagCacheStore::class.java) as Logger
        logger.addAppender(listAppender)

        When("get을 호출하면") {
            val result = cacheStore.get("demo.feature.hello")

            Then("예외를 전파하지 않고 null을 반환한다") {
                result.shouldBeNull()
            }

            Then("redis-degraded 경보(WARN, source=feature-flag)가 로그로 발신된다") {
                val degradedLog = requireNotNull(
                    listAppender.list.find { it.formattedMessage.contains("redis-degraded") },
                ) { "redis-degraded 로그가 발신되지 않았다" }

                degradedLog.level shouldBe Level.WARN
                degradedLog.formattedMessage.contains("source=feature-flag") shouldBe true
            }

            Then("layer=redis,result=miss 카운터가 증가한다") {
                val missCount = meterRegistry.counter(
                    "feature_flag_cache_access_total",
                    "layer",
                    "redis",
                    "result",
                    "miss",
                ).count()
                missCount shouldBe 1.0
            }
        }

        logger.detachAppender(listAppender)
        brokenConnectionFactory.destroy()
    }
}) {
    companion object {
        private const val UNREACHABLE_PORT = 1
    }
}
