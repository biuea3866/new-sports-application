package com.sportsapp.scenario.featureflag

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.SharedTestContainers
import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.presentation.featureflag.dto.CreateFeatureFlagRequest
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

/**
 * Redis(캐시·pub/sub) 접근 불가 상황에서도 이미 로컬(L1)에 채워진 스냅샷으로 평가가
 * 지속되는지(FR-11, 실패 경로 표 "Redis 장애") 검증하는 E2E 시나리오.
 *
 * 컨테이너를 완전히 내리지 않고 일시 중단(`docker pause`)한 뒤 복구한다 — 공유 싱글톤
 * Redis 컨테이너([SharedTestContainers])라 다른 시나리오에 영향을 주지 않도록 반드시 재개한다.
 */
class FeatureFlagRedisFallbackScenarioTest(
    @Autowired private val restTemplate: TestRestTemplate,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val featureFlagEvaluator: FeatureFlagEvaluator,
) : BaseIntegrationTest() {

    private val flagKey = "demo.feature.redis-fallback-${System.nanoTime()}"

    private fun loginAsAdmin(email: String): String {
        val user = userDomainService.register(email, "Password1!")
        userDomainService.assignRole(adminId = user.id, userId = user.id, roleName = "ADMIN")
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val loginBody = objectMapper.writeValueAsString(mapOf("email" to email, "password" to "Password1!"))
        val response = restTemplate.exchange("/auth/login", HttpMethod.POST, HttpEntity(loginBody, headers), String::class.java)
        return objectMapper.readTree(response.body).get("accessToken").asText()
    }

    private fun createFlag(accessToken: String) {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            setBearerAuth(accessToken)
        }
        val request = CreateFeatureFlagRequest(
            key = flagKey,
            type = FeatureFlagType.OPERATIONAL,
            description = "Redis 폴백 시나리오",
            strategy = EvaluationStrategy.GlobalToggle(enabled = true),
        )
        val response = restTemplate.exchange(
            "/admin/feature-flags",
            HttpMethod.POST,
            HttpEntity(objectMapper.writeValueAsString(request), headers),
            String::class.java,
        )
        response.statusCode shouldBe HttpStatus.CREATED
    }

    private fun pauseRedis() {
        SharedTestContainers.redis.dockerClient.pauseContainerCmd(SharedTestContainers.redis.containerId).exec()
    }

    private fun unpauseRedis() {
        runCatching {
            SharedTestContainers.redis.dockerClient.unpauseContainerCmd(SharedTestContainers.redis.containerId).exec()
        }
    }

    init {
        Given("운영 플래그가 생성되어 평가를 한 번 거쳐 로컬 스냅샷이 채워졌을 때") {
            val accessToken = loginAsAdmin("ff-redis-fallback-admin@example.com")
            createFlag(accessToken)

            val context = FeatureContext.anonymous()
            val warmedUpResult = featureFlagEvaluator.isEnabled(flagKey, context, false)

            When("Redis 컨테이너를 일시 중단시키고 다시 평가하면") {
                Then("로컬 스냅샷만으로 평가가 지속되고 예외 없이 동일한 결과를 반환한다") {
                    warmedUpResult shouldBe true

                    pauseRedis()
                    try {
                        val resultWhileRedisDown = featureFlagEvaluator.isEnabled(flagKey, context, false)
                        resultWhileRedisDown shouldBe true
                    } finally {
                        unpauseRedis()
                    }
                }
            }
        }

        afterSpec { unpauseRedis() }
    }
}
