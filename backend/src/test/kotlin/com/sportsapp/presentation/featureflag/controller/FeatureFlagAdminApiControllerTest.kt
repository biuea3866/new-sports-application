package com.sportsapp.presentation.featureflag.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.featureflag.dto.CreateFeatureFlagRequest
import com.sportsapp.presentation.featureflag.dto.UpdateFeatureFlagRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class FeatureFlagAdminApiControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val objectMapper: ObjectMapper,
) : BaseJpaIntegrationTest() {

    private fun registerUser(email: String): User = userDomainService.register(email, "Password1!")

    private fun adminAuth(user: User) =
        SecurityMockMvcRequestPostProcessors.authentication(
            UsernamePasswordAuthenticationToken(
                UserPrincipal(id = user.id, email = user.email, roles = listOf("ADMIN")),
                null,
                listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
            ),
        )

    private fun createFlag(
        admin: User,
        key: String,
        type: FeatureFlagType = FeatureFlagType.RELEASE,
        strategy: EvaluationStrategy = EvaluationStrategy.GlobalToggle(enabled = true),
        description: String? = "테스트 플래그",
    ): String {
        val request = CreateFeatureFlagRequest(key = key, type = type, description = description, strategy = strategy)
        return mockMvc.perform(
            post("/admin/feature-flags")
                .with(adminAuth(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
    }

    init {
        Given("ADMIN 권한을 가진 사용자가 존재할 때") {
            val admin = registerUser("ff-admin-create@example.com")

            When("POST /admin/feature-flags 로 유효한 요청을 보내면") {
                Then("201과 FeatureFlagResponse를 반환한다") {
                    val key = "demo.feature.create-${System.nanoTime()}"
                    val request = CreateFeatureFlagRequest(
                        key = key,
                        type = FeatureFlagType.RELEASE,
                        description = "테스트 플래그",
                        strategy = EvaluationStrategy.GlobalToggle(enabled = true),
                    )
                    mockMvc.perform(
                        post("/admin/feature-flags")
                            .with(adminAuth(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)),
                    )
                        .andExpect(status().isCreated)
                        .andExpect(jsonPath("$.key").value(key))
                        .andExpect(jsonPath("$.status").value("ACTIVE"))
                        .andExpect(jsonPath("$.type").value("RELEASE"))
                        .andExpect(jsonPath("$.strategy.strategyType").value("GLOBAL_TOGGLE"))
                        .andExpect(jsonPath("$.strategy.enabled").value(true))
                        .andExpect(jsonPath("$.createdAt").exists())
                        .andExpect(jsonPath("$.updatedAt").exists())
                }
            }

            When("percentage가 100을 초과하는 전략으로 생성 요청을 보내면") {
                Then("400을 반환한다") {
                    val request = CreateFeatureFlagRequest(
                        key = "demo.feature.percentage-invalid-${System.nanoTime()}",
                        type = FeatureFlagType.RELEASE,
                        description = "잘못된 퍼센티지",
                        strategy = EvaluationStrategy.PercentageRollout(percentage = 150),
                    )
                    mockMvc.perform(
                        post("/admin/feature-flags")
                            .with(adminAuth(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)),
                    ).andExpect(status().isBadRequest)
                }
            }

            When("이미 존재하는 key로 다시 생성 요청을 보내면") {
                Then("400을 반환한다") {
                    val key = "demo.feature.duplicate-${System.nanoTime()}"
                    createFlag(admin, key)
                    val request = CreateFeatureFlagRequest(
                        key = key,
                        type = FeatureFlagType.RELEASE,
                        description = "중복 키",
                        strategy = EvaluationStrategy.GlobalToggle(enabled = true),
                    )
                    mockMvc.perform(
                        post("/admin/feature-flags")
                            .with(adminAuth(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)),
                    ).andExpect(status().isBadRequest)
                }
            }

            When("ENTITLEMENT 타입 플래그가 없을 때 GET 목록을 조회하면") {
                Then("빈 배열을 반환한다") {
                    mockMvc.perform(
                        get("/admin/feature-flags")
                            .param("type", "ENTITLEMENT")
                            .with(adminAuth(admin)),
                    )
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$").isArray)
                        .andExpect(jsonPath("$.length()").value(0))
                }
            }

            When("존재하지 않는 key로 GET 상세를 조회하면") {
                Then("404를 반환한다") {
                    mockMvc.perform(
                        get("/admin/feature-flags/demo.feature.not-exist-${System.nanoTime()}")
                            .with(adminAuth(admin)),
                    ).andExpect(status().isNotFound)
                }
            }
        }

        Given("ADMIN이 플래그를 생성하고 archive한 뒤") {
            val admin = registerUser("ff-admin-archive@example.com")
            val key = "demo.feature.archive-${System.nanoTime()}"
            createFlag(admin, key)
            mockMvc.perform(post("/admin/feature-flags/$key/archive").with(adminAuth(admin)))
                .andExpect(status().isOk)

            When("이미 ARCHIVED된 플래그를 다시 archive 요청하면") {
                Then("409를 반환한다") {
                    mockMvc.perform(
                        post("/admin/feature-flags/$key/archive").with(adminAuth(admin)),
                    ).andExpect(status().isConflict)
                }
            }
        }

        Given("ADMIN이 GLOBAL_TOGGLE 전략의 플래그를 생성한 뒤") {
            val admin = registerUser("ff-admin-update@example.com")
            val key = "demo.feature.update-${System.nanoTime()}"
            createFlag(admin, key, strategy = EvaluationStrategy.GlobalToggle(enabled = true))

            When("PUT으로 PERCENTAGE_ROLLOUT 전략으로 수정 요청하면") {
                Then("strategyType 판별로 전략이 갱신된다") {
                    val request = UpdateFeatureFlagRequest(
                        description = "퍼센티지 롤아웃으로 전환",
                        strategy = EvaluationStrategy.PercentageRollout(percentage = 50),
                    )
                    mockMvc.perform(
                        put("/admin/feature-flags/$key")
                            .with(adminAuth(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)),
                    )
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.strategy.strategyType").value("PERCENTAGE_ROLLOUT"))
                        .andExpect(jsonPath("$.strategy.percentage").value(50))
                }
            }
        }

        Given("ADMIN이 플래그를 생성하고 생성·수정·아카이브로 감사 로그 3건을 쌓은 뒤") {
            val admin = registerUser("ff-admin-audit@example.com")
            val key = "demo.feature.audit-${System.nanoTime()}"
            createFlag(admin, key)
            mockMvc.perform(
                put("/admin/feature-flags/$key")
                    .with(adminAuth(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            UpdateFeatureFlagRequest(
                                description = "수정됨",
                                strategy = EvaluationStrategy.GlobalToggle(enabled = false),
                            ),
                        ),
                    ),
            ).andExpect(status().isOk)
            mockMvc.perform(post("/admin/feature-flags/$key/archive").with(adminAuth(admin)))
                .andExpect(status().isOk)

            When("GET /{key}/audit-logs를 기본 페이지 크기로 조회하면") {
                Then("content·totalElements·totalPages를 담은 최신순 페이지 응답을 반환한다") {
                    mockMvc.perform(
                        get("/admin/feature-flags/$key/audit-logs").with(adminAuth(admin)),
                    )
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(3))
                        .andExpect(jsonPath("$.totalPages").value(1))
                        .andExpect(jsonPath("$.content.length()").value(3))
                        .andExpect(jsonPath("$.content[0].changeType").value("ARCHIVED"))
                        .andExpect(jsonPath("$.content[2].changeType").value("CREATED"))
                }
            }

            When("page·size 쿼리로 조회하면") {
                Then("페이지네이션되고 total로 총페이지가 계산 가능하다") {
                    mockMvc.perform(
                        get("/admin/feature-flags/$key/audit-logs")
                            .param("page", "0")
                            .param("size", "1")
                            .with(adminAuth(admin)),
                    )
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.content.length()").value(1))
                        .andExpect(jsonPath("$.totalElements").value(3))
                        .andExpect(jsonPath("$.totalPages").value(3))
                        .andExpect(jsonPath("$.pageSize").value(1))
                        .andExpect(jsonPath("$.pageNumber").value(0))
                }
            }
        }
    }
}
