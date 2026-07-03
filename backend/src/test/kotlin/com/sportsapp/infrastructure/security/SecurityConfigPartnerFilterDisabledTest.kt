package com.sportsapp.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.application.partner.dto.CreatePartnerResponse
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.domain.user.vo.UserPrincipal
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * partner.auth.enabled=false로 오버라이드했을 때 [PartnerApiKeyAuthenticationFilter]가
 * 필터 체인에 등록되지 않고 휴면 상태로 남는지 검증한다 (별도 Spring 컨텍스트).
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = ["partner.auth.enabled=false"])
class SecurityConfigPartnerFilterDisabledTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val objectMapper: ObjectMapper,
) : BaseJpaIntegrationTest() {

    private fun registerUser(email: String, password: String = "Password1!"): User =
        userDomainService.register(email, password)

    private fun adminAuth(user: User) =
        SecurityMockMvcRequestPostProcessors.authentication(
            UsernamePasswordAuthenticationToken(
                UserPrincipal(id = user.id, email = user.email, roles = listOf("ADMIN")),
                null,
                listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
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
        Given("partner.auth.enabled=false 이고 유효한 파트너 API Key가 발급되어 있을 때") {
            val admin = registerUser("partner-filter-disabled-admin@example.com")
            val created = createPartner(admin, "휴면 테스트 스토어")

            When("발급받은 API Key로 GET /api/goods-seller/products 를 호출하면") {
                Then("필터가 미등록 상태라 인증되지 않고 401을 반환한다") {
                    mockMvc.perform(
                        get("/api/goods-seller/products")
                            .header("Authorization", "Bearer ${created.plainApiKey}"),
                    ).andExpect(status().isUnauthorized)
                }
            }
        }
    }
}
