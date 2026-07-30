package com.sportsapp.presentation.facility.controller

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
 * Release Scenario 2단계·롤백 — `facility.program.enabled=false`면 [ProgramApiController] 빈 자체가
 * 등록되지 않아 전 엔드포인트가 404로 응답한다 (BE-59, LimitedDropApiControllerFeatureFlagDisabledTest 패턴).
 *
 * AUTH-04 — POST는 SecurityConfig 상 authenticated()이므로 유효 JWT를 실어야 "빈 부재 → 404"
 * 경로에 도달한다(JWT 없으면 401로 먼저 막혀 이 테스트의 의도를 검증하지 못한다).
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = ["facility.program.enabled=false"])
class ProgramApiControllerFeatureFlagDisabledTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jwtIssuer: JwtIssuer,
) : BaseIntegrationTest() {

    init {
        Given("facility.program.enabled=false로 비활성화된 컨텍스트") {
            When("GET /facilities/{facilityId}/programs를 호출하면") {
                Then("컨트롤러 빈이 등록되지 않아 404를 반환한다") {
                    val result = mockMvc.perform(get("/facilities/FAC-01/programs")).andReturn()
                    result.response.status shouldBe 404
                }
            }

            When("POST /facilities/{facilityId}/programs를 호출하면") {
                Then("컨트롤러 빈이 등록되지 않아 404를 반환한다") {
                    val result = mockMvc.perform(
                        post("/facilities/FAC-01/programs")
                            .header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(1L))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"name":"1:1 PT","description":null,"price":0,"capacity":1,"durationMinutes":60}"""),
                    ).andReturn()
                    result.response.status shouldBe 404
                }
            }
        }
    }
}
