package com.sportsapp.scenario.featureflag

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.featureflag.dto.FeatureFlagResponse
import com.sportsapp.application.featureflag.dto.ListFeatureFlagAuditLogsResponse
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import com.sportsapp.domain.featureflag.strategy.StableBucketer
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.presentation.featureflag.dto.CreateFeatureFlagRequest
import com.sportsapp.presentation.featureflag.dto.UpdateFeatureFlagRequest
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

/**
 * 관리 API로 운영되는 데모 게이팅 플래그의 생애주기를 실제 HTTP 왕복으로 검증하는 E2E 시나리오.
 *
 * `demo.feature.hello`는 [com.sportsapp.domain.featuredemo.service.FeatureDemoDomainService]가
 * 고정 상수로 게이팅하는 유일한 키이므로, 아래 모든 단계는 그 키의 생애주기를 순서대로 이어간다
 * (생성 → 활성 노출 → 킬스위치 → 재활성 → 퍼센티지 롤아웃 → 감사 커버리지).
 */
class FeatureFlagDemoGatingScenarioTest(
    @Autowired private val restTemplate: TestRestTemplate,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val userDomainService: UserDomainService,
) : BaseIntegrationTest() {

    private val demoFlagKey = "demo.feature.hello"

    private val userExposedFrom10Percent = findUserIdWithBucket(0 until 10)
    private val userExposedFrom50Percent = findUserIdWithBucket(10 until 50)
    private val userExposedFrom100Percent = findUserIdWithBucket(50 until 100)

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

    private fun createFlag(accessToken: String, strategy: EvaluationStrategy): ResponseEntity<String> {
        val request = CreateFeatureFlagRequest(
            key = demoFlagKey,
            type = FeatureFlagType.RELEASE,
            description = "데모 게이팅 시나리오",
            strategy = strategy,
        )
        return restTemplate.exchange(
            "/admin/feature-flags",
            HttpMethod.POST,
            HttpEntity(objectMapper.writeValueAsString(request), authHeaders(accessToken)),
            String::class.java,
        )
    }

    private fun updateFlag(accessToken: String, strategy: EvaluationStrategy): ResponseEntity<String> {
        val request = UpdateFeatureFlagRequest(description = "데모 게이팅 시나리오", strategy = strategy)
        return restTemplate.exchange(
            "/admin/feature-flags/$demoFlagKey",
            HttpMethod.PUT,
            HttpEntity(objectMapper.writeValueAsString(request), authHeaders(accessToken)),
            String::class.java,
        )
    }

    private fun archiveFlag(accessToken: String): ResponseEntity<String> =
        restTemplate.exchange(
            "/admin/feature-flags/$demoFlagKey/archive",
            HttpMethod.POST,
            HttpEntity<Void>(authHeaders(accessToken)),
            String::class.java,
        )

    private fun activateFlag(accessToken: String): ResponseEntity<String> =
        restTemplate.exchange(
            "/admin/feature-flags/$demoFlagKey/activate",
            HttpMethod.POST,
            HttpEntity<Void>(authHeaders(accessToken)),
            String::class.java,
        )

    private fun listFlags(accessToken: String, status: FeatureFlagStatus): List<FeatureFlagResponse> {
        val response = restTemplate.exchange(
            "/admin/feature-flags?status=$status",
            HttpMethod.GET,
            HttpEntity<Void>(authHeaders(accessToken)),
            String::class.java,
        )
        val collectionType = objectMapper.typeFactory.constructCollectionType(List::class.java, FeatureFlagResponse::class.java)
        return objectMapper.readValue(response.body, collectionType)
    }

    private fun auditLogTotal(accessToken: String): Long {
        val response = restTemplate.exchange(
            "/admin/feature-flags/$demoFlagKey/audit-logs",
            HttpMethod.GET,
            HttpEntity<Void>(authHeaders(accessToken)),
            String::class.java,
        )
        return objectMapper.readValue(response.body, ListFeatureFlagAuditLogsResponse::class.java).totalElements
    }

    private fun getDemoHello(userId: Long? = null): ResponseEntity<String> {
        val headers = HttpHeaders()
        userId?.let { headers.set("X-User-Id", it.toString()) }
        return restTemplate.exchange("/feature-demo/hello", HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
    }

    init {
        Given("운영자가 데모 게이팅 플래그를 생성부터 퍼센티지 롤아웃까지 운영할 때") {
            val accessToken = loginAsAdmin("ff-demo-admin@example.com")

            When("GlobalToggle ON으로 플래그를 생성하면") {
                val createResponse = createFlag(accessToken, EvaluationStrategy.GlobalToggle(enabled = true))

                Then("201 Created가 반환된다") {
                    createResponse.statusCode shouldBe HttpStatus.CREATED
                }

                Then("데모 엔드포인트가 200을 반환한다") {
                    getDemoHello().statusCode shouldBe HttpStatus.OK
                }
            }

            When("플래그를 archive 하면") {
                val archiveResponse = archiveFlag(accessToken)

                Then("200이 반환된다") {
                    archiveResponse.statusCode shouldBe HttpStatus.OK
                }

                Then("데모 엔드포인트가 재배포 없이 503을 반환한다") {
                    eventually(3.seconds) {
                        getDemoHello().statusCode shouldBe HttpStatus.SERVICE_UNAVAILABLE
                    }
                }

                Then("활성 목록 조회에서 제외되고 아카이브 목록 조회에는 포함된다") {
                    listFlags(accessToken, FeatureFlagStatus.ACTIVE).none { it.key == demoFlagKey } shouldBe true
                    listFlags(accessToken, FeatureFlagStatus.ARCHIVED).any { it.key == demoFlagKey } shouldBe true
                }
            }

            When("플래그를 다시 activate 하면") {
                val activateResponse = activateFlag(accessToken)

                Then("200이 반환된다") {
                    activateResponse.statusCode shouldBe HttpStatus.OK
                }

                Then("수 초 내에 데모 엔드포인트가 200으로 복구된다") {
                    eventually(3.seconds) {
                        getDemoHello().statusCode shouldBe HttpStatus.OK
                    }
                }
            }

            When("PercentageRollout을 10%로 설정하면") {
                updateFlag(accessToken, EvaluationStrategy.PercentageRollout(percentage = 10))

                Then("버킷 10 미만인 사용자만 노출되고 나머지는 전파가 끝나면 제외된다") {
                    eventually(3.seconds) {
                        getDemoHello(userExposedFrom100Percent).statusCode shouldBe HttpStatus.SERVICE_UNAVAILABLE
                    }
                    getDemoHello(userExposedFrom10Percent).statusCode shouldBe HttpStatus.OK
                    getDemoHello(userExposedFrom50Percent).statusCode shouldBe HttpStatus.SERVICE_UNAVAILABLE
                }
            }

            When("PercentageRollout을 50%로 올리면") {
                updateFlag(accessToken, EvaluationStrategy.PercentageRollout(percentage = 50))

                Then("이미 노출됐던 사용자는 계속 노출되고 새로 편입된 사용자도 노출된다(단조 증가)") {
                    eventually(3.seconds) {
                        getDemoHello(userExposedFrom50Percent).statusCode shouldBe HttpStatus.OK
                    }
                    getDemoHello(userExposedFrom10Percent).statusCode shouldBe HttpStatus.OK
                    getDemoHello(userExposedFrom100Percent).statusCode shouldBe HttpStatus.SERVICE_UNAVAILABLE
                }
            }

            When("PercentageRollout을 100%로 올리면") {
                updateFlag(accessToken, EvaluationStrategy.PercentageRollout(percentage = 100))

                Then("모든 사용자가 노출되며 기존 노출 사용자도 여전히 노출된다") {
                    eventually(3.seconds) {
                        getDemoHello(userExposedFrom100Percent).statusCode shouldBe HttpStatus.OK
                    }
                    getDemoHello(userExposedFrom10Percent).statusCode shouldBe HttpStatus.OK
                    getDemoHello(userExposedFrom50Percent).statusCode shouldBe HttpStatus.OK
                }
            }

            When("지금까지의 변경 이력을 감사 로그로 조회하면") {
                Then("관리 변경 성공 건수(생성 1 + 아카이브 1 + 활성화 1 + 퍼센티지 수정 3 = 6건)와 감사 로그 적재 건수가 일치한다") {
                    auditLogTotal(accessToken) shouldBe 6L
                }
            }
        }
    }

    companion object {
        private const val MAX_BUCKET_SEARCH_ATTEMPTS = 100_000L

        private fun findUserIdWithBucket(range: IntRange, flagKey: String = "demo.feature.hello"): Long {
            var candidate = 1L
            while (candidate <= MAX_BUCKET_SEARCH_ATTEMPTS) {
                if (StableBucketer.bucket(flagKey, candidate) in range) return candidate
                candidate++
            }
            error("bucket range $range 에 해당하는 userId를 $MAX_BUCKET_SEARCH_ATTEMPTS 회 이내에 찾지 못했다")
        }
    }
}
