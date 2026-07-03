package com.sportsapp.scenario.partner

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.application.partner.dto.CreatePartnerResponse
import com.sportsapp.application.partner.dto.ReissueApiKeyResponse
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.domain.user.vo.UserPrincipal
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * B2B 파트너 연동 E2E: 권한·소유권 경계와 API Key 라이프사이클,
 * 감사 로그 커버리지를 실 구동으로 검증한다.
 */
@AutoConfigureMockMvc
class PartnerAccessControlScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val objectMapper: ObjectMapper,
) : BaseJpaIntegrationTest() {

    private fun registerAdmin(): User = userDomainService.register("access-admin-${UUID.randomUUID()}@example.com", "Password1!")

    private fun adminAuth(admin: User) =
        SecurityMockMvcRequestPostProcessors.authentication(
            UsernamePasswordAuthenticationToken(
                UserPrincipal(id = admin.id, email = admin.email, roles = listOf("ADMIN")),
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

    private fun createProductRequestBody(category: String = "EQUIPMENT"): String =
        objectMapper.writeValueAsString(
            mapOf(
                "name" to "파트너 등록 상품",
                "category" to category,
                "price" to 45000,
                "description" to "권한 검증용 상품",
                "imageUrl" to "https://example.com/access-product.png",
            ),
        )

    private fun createProductWithKey(plainApiKey: String, category: String = "EQUIPMENT") =
        mockMvc.perform(
            post("/api/goods-seller/products")
                .header("Authorization", "Bearer $plainApiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createProductRequestBody(category)),
        )

    init {
        Given("파트너가 유효한 API Key를 보유하고 있을 때") {
            val admin = registerAdmin()
            val created = createPartner(admin, "무효 카테고리 검증 파트너")

            When("무효한 카테고리로 상품 등록을 요청하면") {
                Then("400을 반환한다") {
                    createProductWithKey(created.plainApiKey, category = "NOT_A_CATEGORY")
                        .andExpect(status().isBadRequest)
                }
            }
        }

        Given("서로 다른 두 파트너가 각각 API Key를 보유하고 있을 때") {
            val admin = registerAdmin()
            val partnerA = createPartner(admin, "소유권 검증 파트너 A")
            val partnerB = createPartner(admin, "소유권 검증 파트너 B")

            val productABody = createProductWithKey(partnerA.plainApiKey)
                .andExpect(status().isCreated)
                .andReturn().response.contentAsString
            val productAId = objectMapper.readTree(productABody).get("id").asLong()

            When("파트너 B의 API Key로 파트너 A 소유 상품을 수정하려 하면") {
                Then("404를 반환한다") {
                    mockMvc.perform(
                        patch("/api/goods-seller/products/$productAId")
                            .header("Authorization", "Bearer ${partnerB.plainApiKey}")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                objectMapper.writeValueAsString(
                                    mapOf(
                                        "name" to "탈취 시도",
                                        "category" to "EQUIPMENT",
                                        "price" to 1000,
                                        "description" to "타 파트너 상품 수정 시도",
                                        "imageUrl" to "https://example.com/hijack.png",
                                    ),
                                ),
                            ),
                    ).andExpect(status().isNotFound)
                }
            }
        }

        Given("파트너의 API Key를 재발급했을 때") {
            val admin = registerAdmin()
            val created = createPartner(admin, "재발급 검증 파트너")

            val reissuedBody = mockMvc.perform(
                post("/api/admin/partners/${created.partnerId}/api-keys")
                    .with(adminAuth(admin)),
            ).andExpect(status().isCreated).andReturn().response.contentAsString
            val reissued = objectMapper.readValue(reissuedBody, ReissueApiKeyResponse::class.java)

            When("폐기된 구 API Key로 상품 등록을 요청하면") {
                Then("401을 반환한다") {
                    createProductWithKey(created.plainApiKey).andExpect(status().isUnauthorized)
                }
            }

            When("재발급받은 신규 API Key로 상품 등록을 요청하면") {
                Then("201을 반환한다") {
                    createProductWithKey(reissued.plainApiKey).andExpect(status().isCreated)
                }
            }
        }

        Given("파트너가 SUSPENDED 상태로 전환됐을 때") {
            val admin = registerAdmin()
            val created = createPartner(admin, "정지 검증 파트너")

            mockMvc.perform(
                patch("/api/admin/partners/${created.partnerId}/status")
                    .with(adminAuth(admin))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("status" to "SUSPENDED"))),
            ).andExpect(status().isOk)

            When("해당 파트너의 API Key로 상품 등록을 요청하면") {
                Then("403을 반환한다") {
                    createProductWithKey(created.plainApiKey).andExpect(status().isForbidden)
                }
            }
        }

        Given("파트너의 등록 요청이 성공·실패 각각 발생했을 때") {
            val admin = registerAdmin()
            val created = createPartner(admin, "감사 로그 커버리지 검증 파트너")

            createProductWithKey(created.plainApiKey).andExpect(status().isCreated)
            createProductWithKey(created.plainApiKey, category = "NOT_A_CATEGORY").andExpect(status().isBadRequest)

            When("잠시 후 감사 로그를 조회하면") {
                Then("성공·실패 요청이 모두 partner_audit_log에 적재된다") {
                    eventually(5.seconds) {
                        val auditBody = mockMvc.perform(
                            get("/api/admin/partners/${created.partnerId}/audit-logs")
                                .with(adminAuth(admin)),
                        ).andExpect(status().isOk).andReturn().response.contentAsString

                        val content = objectMapper.readTree(auditBody).get("content")
                        content.size() shouldBe 2
                        val statusCodes = content.map { it.get("statusCode").asInt() }
                        statusCodes shouldContain 201
                        statusCodes shouldContain 400
                    }
                }
            }
        }
    }
}
