package com.sportsapp.presentation.goods.controller

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.user.gateway.JwtIssuer
import com.sportsapp.presentation.support.bearerTokenFor
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * Release Scenario 2단계·롤백 — `limited-drop.enabled=false`면 [LimitedDropApiController] 빈 자체가
 * 등록되지 않아 전 엔드포인트가 404로 응답한다. 별도 Spring 컨텍스트(다른 프로퍼티) 필요해
 * [LimitedDropApiControllerTest]와 분리한다.
 *
 * AUTH-04 — POST는 SecurityConfig 상 authenticated()이므로 유효 JWT를 실어야 "빈 부재 → 404"
 * 경로에 도달한다.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = ["limited-drop.enabled=false"])
class LimitedDropApiControllerFeatureFlagDisabledTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jwtIssuer: JwtIssuer,
) : BaseIntegrationTest() {

    init {
        Given("limited-drop.enabled=false로 비활성화된 컨텍스트") {
            When("GET /limited-drops/{dropId}를 호출하면") {
                Then("컨트롤러 빈이 등록되지 않아 404를 반환한다") {
                    val result = mockMvc.perform(get("/limited-drops/1")).andReturn()
                    result.response.status shouldBe 404
                }
            }

            When("POST /limited-drops/{dropId}/orders를 호출하면") {
                Then("컨트롤러 빈이 등록되지 않아 404를 반환한다") {
                    val result = mockMvc.perform(
                        post("/limited-drops/1/orders")
                            .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(1L))
                            .header("Idempotency-Key", "key-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"quantity":1}""")
                    ).andReturn()
                    result.response.status shouldBe 404
                }
            }
        }
    }
}
