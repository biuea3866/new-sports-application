package com.sportsapp.scenario.featureflag

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.SharedTestContainers
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.repository.FeatureFlagRepository
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.infrastructure.config.FeatureFlagRedisPubSubConfig
import com.sportsapp.infrastructure.featureflag.local.LocalFeatureFlagStore
import com.sportsapp.infrastructure.featureflag.metrics.FeatureFlagCacheMetrics
import com.sportsapp.presentation.featureflag.dto.CreateFeatureFlagRequest
import com.sportsapp.infrastructure.featureflag.redis.FeatureFlagChangeSubscriber
import com.sportsapp.infrastructure.featureflag.redis.RedisFeatureFlagCacheStore
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

/**
 * 같은 Redis에 붙은 2개의 독립 구독자(가상의 2 인스턴스)가 관리 API로 트리거된 킬스위치 변경을
 * 각각 3초 이내에 로컬에 반영하는지 검증한다(SM3, 경량 다중 인스턴스 E2E — 20000TPS 부하 실측은
 * 형제 과제 위임 범위 밖, 전파 시간만 검증).
 */
class FeatureFlagMultiInstancePropagationScenarioTest(
    @Autowired private val restTemplate: TestRestTemplate,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val featureFlagRepository: FeatureFlagRepository,
) : BaseIntegrationTest() {

    private class ShadowSubscriberInstance(featureFlagRepository: FeatureFlagRepository) {
        private val instanceObjectMapper: ObjectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())
        val meterRegistry = SimpleMeterRegistry()
        private val connectionFactory = LettuceConnectionFactory(
            RedisStandaloneConfiguration(SharedTestContainers.redis.host, SharedTestContainers.redis.getMappedPort(6379)),
        ).apply { afterPropertiesSet() }
        private val stringRedisTemplate = StringRedisTemplate(connectionFactory).apply { afterPropertiesSet() }
        private val cacheStore = RedisFeatureFlagCacheStore(stringRedisTemplate, instanceObjectMapper, meterRegistry)
        val localStore = LocalFeatureFlagStore(cacheStore, featureFlagRepository, meterRegistry)
        private val subscriber = FeatureFlagChangeSubscriber(localStore, instanceObjectMapper, meterRegistry)
        private val container = FeatureFlagRedisPubSubConfig()
            .featureFlagRedisMessageListenerContainer(connectionFactory, subscriber)
            .apply {
                afterPropertiesSet()
                start()
            }

        fun close() {
            container.stop()
            connectionFactory.destroy()
        }
    }

    private fun loginAsAdmin(email: String): String {
        val user = userDomainService.register(email, "Password1!")
        userDomainService.assignRole(adminId = user.id, userId = user.id, roleName = "ADMIN")
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val loginBody = objectMapper.writeValueAsString(mapOf("email" to email, "password" to "Password1!"))
        val response = restTemplate.exchange("/auth/login", HttpMethod.POST, HttpEntity(loginBody, headers), String::class.java)
        return objectMapper.readTree(response.body).get("accessToken").asText()
    }

    private fun authHeaders(accessToken: String): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        setBearerAuth(accessToken)
    }

    private fun createFlag(accessToken: String, key: String) {
        val request = CreateFeatureFlagRequest(
            key = key,
            type = FeatureFlagType.OPERATIONAL,
            description = "다중 인스턴스 전파 시나리오",
            strategy = EvaluationStrategy.GlobalToggle(enabled = true),
        )
        val response = restTemplate.exchange(
            "/admin/feature-flags",
            HttpMethod.POST,
            HttpEntity(objectMapper.writeValueAsString(request), authHeaders(accessToken)),
            String::class.java,
        )
        response.statusCode shouldBe HttpStatus.CREATED
    }

    private fun archiveFlag(accessToken: String, key: String) =
        restTemplate.exchange(
            "/admin/feature-flags/$key/archive",
            HttpMethod.POST,
            HttpEntity<Void>(authHeaders(accessToken)),
            String::class.java,
        )

    init {
        Given("동일 Redis를 공유하는 2개의 독립 구독 인스턴스가 떠 있을 때") {
            val accessToken = loginAsAdmin("ff-multi-instance-admin@example.com")
            val flagKey = "demo.feature.multi-instance-${System.nanoTime()}"
            createFlag(accessToken, flagKey)

            val instanceA = ShadowSubscriberInstance(featureFlagRepository)
            val instanceB = ShadowSubscriberInstance(featureFlagRepository)

            try {
                When("관리 API로 플래그를 archive(킬스위치)하면") {
                    val archiveResponse = archiveFlag(accessToken, flagKey)

                    Then("200이 반환된다") {
                        archiveResponse.statusCode shouldBe HttpStatus.OK
                    }

                    Then("첫 번째 구독 인스턴스가 3초 이내에 로컬 반영을 완료한다") {
                        eventually(3.seconds) {
                            instanceA.localStore.get(flagKey)?.status shouldBe FeatureFlagStatus.ARCHIVED
                        }
                        val recordedLagMillis = instanceA.meterRegistry
                            .find(FeatureFlagCacheMetrics.PROPAGATION_LAG_TIMER)
                            .timer()
                            ?.max(TimeUnit.MILLISECONDS)
                        recordedLagMillis?.let { it shouldBeLessThan 3.seconds.inWholeMilliseconds.toDouble() }
                    }

                    Then("두 번째 구독 인스턴스도 3초 이내에 로컬 반영을 완료한다") {
                        eventually(3.seconds) {
                            instanceB.localStore.get(flagKey)?.status shouldBe FeatureFlagStatus.ARCHIVED
                        }
                        val recordedLagMillis = instanceB.meterRegistry
                            .find(FeatureFlagCacheMetrics.PROPAGATION_LAG_TIMER)
                            .timer()
                            ?.max(TimeUnit.MILLISECONDS)
                        recordedLagMillis?.let { it shouldBeLessThan 3.seconds.inWholeMilliseconds.toDouble() }
                    }
                }
            } finally {
                instanceA.close()
                instanceB.close()
            }
        }
    }
}
