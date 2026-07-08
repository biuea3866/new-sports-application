package com.sportsapp.infrastructure.security

import com.sportsapp.BaseJpaIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// BE-12: `/rooms` 하위 경로 전체가 permitAll -> authenticated() 로 승격되고, 다른 기존 permitAll
// 경로(`/products` 하위 전체)는 회귀 없이 유지되는지 검증한다.
@AutoConfigureMockMvc
class SecurityConfigRoomAuthTest(
    @Autowired private val mockMvc: MockMvc,
) : BaseJpaIntegrationTest() {

    init {
        Given("인증 헤더 없이 /rooms/** 를 호출할 때") {
            When("GET /rooms/me 를 호출하면") {
                Then("401 Unauthorized 가 반환된다 (authenticated 승격 확인)") {
                    mockMvc.perform(get("/rooms/me")).andExpect(status().isUnauthorized)
                }
            }

            When("GET /rooms/1/messages 를 호출하면") {
                Then("/rooms/** 하위 경로도 401 Unauthorized 가 반환된다") {
                    mockMvc.perform(get("/rooms/1/messages")).andExpect(status().isUnauthorized)
                }
            }
        }

        Given("인증 헤더 없이 기존 permitAll 경로(/products/**)를 호출할 때") {
            When("GET /products 를 호출하면") {
                Then("401 이 아니다 (permitAll 회귀 없음 — 컨트롤러 미등록 프로파일이라 404)") {
                    mockMvc.perform(get("/products"))
                        .andExpect { result -> assert(result.response.status != 401) }
                }
            }
        }
    }
}
