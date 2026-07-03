package com.sportsapp.presentation.partner.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.application.partner.dto.CreatePartnerResponse
import com.sportsapp.application.partner.dto.ReissueApiKeyResponse
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.domain.user.vo.UserPrincipal
import org.hamcrest.Matchers.not
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class PartnerAdminApiControllerTest(
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

    private fun nonAdminAuth(user: User) =
        SecurityMockMvcRequestPostProcessors.authentication(
            UsernamePasswordAuthenticationToken(
                UserPrincipal(id = user.id, email = user.email, roles = listOf("USER")),
                null,
                listOf(SimpleGrantedAuthority("ROLE_USER")),
            ),
        )

    private fun createPartner(admin: User, name: String): CreatePartnerResponse {
        val body = mockMvc.perform(
            post("/api/admin/partners")
                .with(adminAuth(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("name" to name))),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return objectMapper.readValue(body, CreatePartnerResponse::class.java)
    }

    init {
        Given("ADMIN 권한을 가진 사용자가 존재할 때") {
            val admin = registerUser("partner-admin-create@example.com")

            When("POST /api/admin/partners 로 파트너 생성을 요청하면") {
                Then("201과 plainApiKey를 포함한 응답을 반환한다") {
                    mockMvc.perform(
                        post("/api/admin/partners")
                            .with(adminAuth(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(mapOf("name" to "런닝화 스토어"))),
                    )
                        .andExpect(status().isCreated)
                        .andExpect(jsonPath("$.name").value("런닝화 스토어"))
                        .andExpect(jsonPath("$.status").value("ACTIVE"))
                        .andExpect(jsonPath("$.plainApiKey").isNotEmpty)
                }
            }
        }

        Given("ADMIN 권한이 없는 사용자가 존재할 때") {
            val nonAdmin = registerUser("partner-non-admin@example.com")

            When("POST /api/admin/partners 를 호출하면") {
                Then("403을 반환한다") {
                    mockMvc.perform(
                        post("/api/admin/partners")
                            .with(nonAdminAuth(nonAdmin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(mapOf("name" to "무권한 스토어"))),
                    ).andExpect(status().isForbidden)
                }
            }
        }

        Given("ADMIN이 파트너를 생성한 뒤") {
            val admin = registerUser("partner-admin-reissue@example.com")
            val created = createPartner(admin, "재발급 테스트 스토어")

            When("POST /{partnerId}/api-keys 로 키 재발급을 요청하면") {
                Then("201과 새 plainApiKey를 반환한다") {
                    mockMvc.perform(
                        post("/api/admin/partners/${created.partnerId}/api-keys")
                            .with(adminAuth(admin)),
                    )
                        .andExpect(status().isCreated)
                        .andExpect(jsonPath("$.plainApiKey").isNotEmpty)
                        .andExpect(jsonPath("$.plainApiKey").value(not(created.plainApiKey)))
                }
            }
        }

        Given("ADMIN이 파트너를 생성하고 키를 재발급한 뒤") {
            val admin = registerUser("partner-admin-revoke@example.com")
            val created = createPartner(admin, "폐기 테스트 스토어")
            val reissueBody = mockMvc.perform(
                post("/api/admin/partners/${created.partnerId}/api-keys")
                    .with(adminAuth(admin)),
            ).andExpect(status().isCreated).andReturn().response.contentAsString
            val reissued = objectMapper.readValue(reissueBody, ReissueApiKeyResponse::class.java)

            When("DELETE /{partnerId}/api-keys/{keyId} 로 키 폐기를 요청하면") {
                Then("204를 반환한다") {
                    mockMvc.perform(
                        delete("/api/admin/partners/${created.partnerId}/api-keys/${reissued.keyId}")
                            .with(adminAuth(admin)),
                    ).andExpect(status().isNoContent)
                }
            }
        }

        Given("ADMIN이 상태 변경 대상 파트너를 생성한 뒤") {
            val admin = registerUser("partner-admin-status@example.com")
            val created = createPartner(admin, "상태 변경 테스트 스토어")

            When("PATCH /{partnerId}/status 로 SUSPENDED 전환을 요청하면") {
                Then("200과 변경된 status를 반환한다") {
                    mockMvc.perform(
                        patch("/api/admin/partners/${created.partnerId}/status")
                            .with(adminAuth(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(mapOf("status" to "SUSPENDED"))),
                    )
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.status").value("SUSPENDED"))
                        .andExpect(jsonPath("$.partnerId").value(created.partnerId))
                }
            }

            When("PATCH /{partnerId}/status 에 잘못된 status 값을 보내면") {
                Then("400을 반환한다") {
                    mockMvc.perform(
                        patch("/api/admin/partners/${created.partnerId}/status")
                            .with(adminAuth(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(mapOf("status" to "UNKNOWN"))),
                    ).andExpect(status().isBadRequest)
                }
            }
        }

        Given("ADMIN이 신규 파트너를 생성한 뒤") {
            val admin = registerUser("partner-admin-audit@example.com")
            val created = createPartner(admin, "감사 로그 테스트 스토어")

            When("GET /{partnerId}/audit-logs 를 호출하면") {
                Then("빈 Page를 반환한다") {
                    mockMvc.perform(
                        get("/api/admin/partners/${created.partnerId}/audit-logs")
                            .with(adminAuth(admin)),
                    )
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.content").isArray)
                        .andExpect(jsonPath("$.content.length()").value(0))
                        .andExpect(jsonPath("$.totalElements").value(0))
                }
            }
        }
    }
}
