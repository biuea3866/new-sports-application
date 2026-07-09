package com.sportsapp.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.presentation.user.dto.response.LoginResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// BE-09: catalog·order 통합 파사드(BE-07/08)의 인가 matcher가 SecurityConfig에 등록되는지 검증한다.
// `/api/catalog`는 permitAll, `/api/orders`는 authenticated여야 한다.
// CatalogCompositionService가 facility(Mongo) 도메인의 ProgramDomainService에 의존해
// test-jpa 프로파일(Mongo 빈 제외)로는 컨텍스트가 뜨지 않으므로, 전체 스택(Mongo/Redis/Minio
// 포함)의 BaseIntegrationTest를 사용한다.
@AutoConfigureMockMvc
class SecurityConfigCatalogOrderAuthTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val objectMapper: ObjectMapper,
) : BaseIntegrationTest() {

    private fun registerUser(email: String, password: String = "Password1!"): Long =
        userDomainService.register(email, password).id

    private fun login(email: String, password: String = "Password1!"): String {
        val body = mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return objectMapper.readValue(body, LoginResponse::class.java).accessToken
    }

    init {
        Given("인증 헤더 없이 /api/catalog 를 호출할 때") {
            When("GET /api/catalog 를 호출하면") {
                Then("200 이 반환된다 (permitAll)") {
                    mockMvc.perform(get("/api/catalog")).andExpect(status().isOk)
                }
            }
        }

        Given("인증 헤더 없이 /api/orders 를 호출할 때") {
            When("GET /api/orders 를 호출하면") {
                Then("401 Unauthorized 가 반환된다 (authenticated)") {
                    mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized)
                }
            }
        }

        Given("JWT로 인증된 사용자가 있을 때") {
            val email = "catalog-order-auth@example.com"
            registerUser(email)
            val token = login(email)

            When("A가 GET /api/orders 를 자신의 토큰으로 호출하면") {
                Then("200 이 반환된다") {
                    mockMvc.perform(
                        get("/api/orders").header("Authorization", "Bearer $token"),
                    ).andExpect(status().isOk)
                }
            }

            When("A가 GET /api/catalog 를 자신의 토큰으로 호출하면") {
                Then("인증 상태에서도 200 이 반환된다") {
                    mockMvc.perform(
                        get("/api/catalog").header("Authorization", "Bearer $token"),
                    ).andExpect(status().isOk)
                }
            }
        }

        Given("인증 헤더 없이 기존 permitAll 경로(/products/**)를 호출할 때") {
            When("GET /products 를 호출하면") {
                Then("401 이 아니다 (permitAll 회귀 없음)") {
                    mockMvc.perform(get("/products"))
                        .andExpect { result -> assert(result.response.status != 401) }
                }
            }
        }
    }
}
