package com.sportsapp.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.application.partner.dto.CreatePartnerResponse
import com.sportsapp.domain.common.UserRoleName
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.user.dto.response.LoginResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * partner.auth.enabled=true(test 프로파일 기본값)에서
 * [PartnerApiKeyAuthenticationFilter] 등록·admin matcher·기존 JWT 경로 회귀를 검증한다.
 */
@AutoConfigureMockMvc
class SecurityConfigPartnerFilterTest(
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

    private fun login(email: String, password: String): String {
        val body = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return objectMapper.readValue(body, LoginResponse::class.java).accessToken
    }

    init {
        Given("파트너 관리자가 신규 파트너를 등록하고 API Key를 발급받았을 때") {
            val admin = registerUser("partner-filter-admin@example.com")
            val created = createPartner(admin, "제휴 스토어")

            When("발급받은 API Key로 GET /api/goods-seller/products 를 호출하면") {
                Then("연동 User principal로 인증되어 200을 반환한다") {
                    mockMvc.perform(
                        get("/api/goods-seller/products")
                            .header("Authorization", "Bearer ${created.plainApiKey}"),
                    ).andExpect(status().isOk)
                }
            }

            When("무권한 JWT 사용자가 GET /api/admin/partners/{id}/audit-logs 를 호출하면") {
                val nonAdminPassword = "NonAdmin1!"
                registerUser("partner-filter-non-admin@example.com", nonAdminPassword)
                val accessToken = login("partner-filter-non-admin@example.com", nonAdminPassword)

                Then("403을 반환한다") {
                    mockMvc.perform(
                        get("/api/admin/partners/${created.partnerId}/audit-logs")
                            .header("Authorization", "Bearer $accessToken"),
                    ).andExpect(status().isForbidden)
                }
            }
        }

        Given("JWT로 인증된 GOODS_SELLER 사용자가 있을 때 (Partner 필터 등록 후 회귀 확인)") {
            val adminPassword = "AdminUser1!"
            val admin = registerUser("partner-filter-regression-admin@example.com", adminPassword)
            val sellerPassword = "SellerUser1!"
            val seller = registerUser("partner-filter-regression-seller@example.com", sellerPassword)
            userDomainService.assignRole(admin.id, seller.id, UserRoleName.GOODS_SELLER.name)
            val accessToken = login("partner-filter-regression-seller@example.com", sellerPassword)

            When("GET /api/goods-seller/products 를 JWT로 호출하면") {
                Then("Partner 필터 등록 후에도 정상적으로 200을 반환한다") {
                    mockMvc.perform(
                        get("/api/goods-seller/products")
                            .header("Authorization", "Bearer $accessToken"),
                    ).andExpect(status().isOk)
                }
            }
        }
    }
}
