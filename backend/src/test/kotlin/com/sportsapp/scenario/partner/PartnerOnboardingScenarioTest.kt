package com.sportsapp.scenario.partner

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.application.partner.dto.CreatePartnerResponse
import com.sportsapp.application.ticketing.dto.CreateMyEventResult
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.domain.user.vo.UserPrincipal
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * B2B 파트너 연동 E2E: 운영자의 파트너 등록·API Key 발급부터
 * 발급받은 키로 기존 등록 엔드포인트(코드 무변경)를 경유해 상품·이벤트를 등록하는
 * 해피 패스 전 흐름을 실 구동으로 검증한다.
 */
@AutoConfigureMockMvc
class PartnerOnboardingScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    private fun registerAdmin(): User = userDomainService.register("onboarding-admin-${UUID.randomUUID()}@example.com", "Password1!")

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

    private fun linkedUserIdOf(partnerId: Long): Long =
        requireNotNull(
            jdbcTemplate.queryForObject(
                "SELECT linked_user_id FROM partner WHERE id = ?",
                Long::class.java,
                partnerId,
            ),
        )

    private fun ownerIdOfProduct(productId: Long): Long =
        requireNotNull(
            jdbcTemplate.queryForObject(
                "SELECT owner_id FROM products WHERE id = ?",
                Long::class.java,
                productId,
            ),
        )

    private fun createProductRequestBody(category: String = "EQUIPMENT"): String =
        objectMapper.writeValueAsString(
            mapOf(
                "name" to "파트너 등록 러닝화",
                "category" to category,
                "price" to 89000,
                "description" to "파트너 연동 경유 등록 상품",
                "imageUrl" to "https://example.com/partner-product.png",
            ),
        )

    private fun createEventRequestBody(seatCount: Int): String {
        val seats = (1..seatCount).joinToString(",") { index ->
            """{"sectionName":"A","seatLabel":"$index","price":50000}"""
        }
        return """
            {
                "title": "파트너 연동 이벤트",
                "venue": "파트너 아레나",
                "startsAt": "2027-08-01T18:00:00+09:00",
                "seats": [$seats]
            }
        """.trimIndent()
    }

    init {
        Given("운영자가 파트너를 등록하고 API Key를 발급받았을 때") {
            val admin = registerAdmin()
            val created = createPartner(admin, "런닝화 파트너 스토어")

            When("발급받은 API Key로 POST /api/goods-seller/products 를 호출하면") {
                Then("201을 반환하고 등록된 상품의 소유자가 연동 User와 일치한다") {
                    val responseBody = mockMvc.perform(
                        post("/api/goods-seller/products")
                            .header("Authorization", "Bearer ${created.plainApiKey}")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createProductRequestBody()),
                    ).andExpect(status().isCreated)
                        .andExpect(jsonPath("$.name").value("파트너 등록 러닝화"))
                        .andReturn().response.contentAsString

                    val productId = objectMapper.readTree(responseBody).get("id").asLong()
                    ownerIdOfProduct(productId) shouldBe linkedUserIdOf(created.partnerId)
                }
            }

            When("같은 API Key로 POST /api/event-host/events 를 호출하면") {
                Then("201을 반환하고 요청한 좌석 수만큼 좌석이 생성된다") {
                    val responseBody = mockMvc.perform(
                        post("/api/event-host/events")
                            .header("Authorization", "Bearer ${created.plainApiKey}")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createEventRequestBody(2)),
                    ).andExpect(status().isCreated).andReturn().response.contentAsString

                    val result = objectMapper.readValue(responseBody, CreateMyEventResult::class.java)
                    result.seatCount shouldBe 2
                }
            }
        }

        Given("신규 파트너가 등록 이력이 없을 때") {
            val admin = registerAdmin()
            val created = createPartner(admin, "신규 무등록 파트너 스토어")

            When("발급받은 API Key로 GET /api/goods-seller/products 를 호출하면") {
                Then("빈 목록을 반환한다") {
                    mockMvc.perform(
                        get("/api/goods-seller/products")
                            .header("Authorization", "Bearer ${created.plainApiKey}"),
                    )
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.content").isArray)
                        .andExpect(jsonPath("$.content.length()").value(0))
                }
            }
        }
    }
}
