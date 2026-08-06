package com.sportsapp.infrastructure.security

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.common.security.InternalCallHeaders
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val CALL_TOKEN = "internal-call-token-for-test"

/**
 * 내부 신원 검증 엔드포인트의 접근 경계를 고정한다 (W1-06a 생성, W1-06b ④ → **S2-07 에서 전환**).
 *
 * **경계가 401 에서 404 로 바뀌었다.** 1단계에는 이 엔드포인트를 부르는 소비자가 없어
 * `anyRequest().authenticated()` 아래 두고 401 로 닫아 뒀다. 2단계에 edge 가 별 프로세스가 되면
 * 그 호출은 HTTP 를 타는데, JWT 를 들고 오지 않으므로 401 이면 정상 호출까지 막힌다.
 *
 * 그래서 S2-07 이 인가를 `permitAll` 로 열고 **호출자 인증을 `InternalCallAuthenticationFilter`
 * 가 공유 시크릿으로 수행**한다. 보호 강도는 낮아지지 않는다 — 오히려 404 는 401 과 달리
 * **경로 존재조차 알려주지 않는다.** 이 엔드포인트가 "이 자격증명이 유효한가"를 답하는 오라클이라는
 * 성질은 그대로이므로, 토큰 없는 호출이 본문에 닿지 않는다는 사실을 여기서 고정한다.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = ["internal.call-token=$CALL_TOKEN"])
class InternalVerificationEndpointAuthTest(
    @Autowired private val mockMvc: MockMvc,
) : BaseJpaIntegrationTest() {

    init {
        Given("호출자 토큰 없이 내부 검증 엔드포인트를 호출할 때") {
            When("POST /internal/mcp-tokens/verify 를 호출하면") {
                Then("404 가 반환된다 — 오라클이 외부에 열리지 않고 경로 존재도 드러내지 않는다") {
                    mockMvc.perform(
                        post("/internal/mcp-tokens/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"token":"mcp_1_probe"}"""),
                    ).andExpect(status().isNotFound)
                }
            }

            When("POST /internal/partner-api-keys/verify 를 호출하면") {
                Then("404 가 반환된다") {
                    mockMvc.perform(
                        post("/internal/partner-api-keys/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"apiKey":"partner_1_probe"}"""),
                    ).andExpect(status().isNotFound)
                }
            }
        }

        Given("틀린 호출자 토큰으로 호출할 때") {
            When("POST /internal/mcp-tokens/verify 를 호출하면") {
                Then("404 가 반환된다 — 토큰 대조 실패도 존재를 드러내지 않는다") {
                    mockMvc.perform(
                        post("/internal/mcp-tokens/verify")
                            .header(InternalCallHeaders.CALL_TOKEN, "wrong-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"token":"mcp_1_probe"}"""),
                    ).andExpect(status().isNotFound)
                }
            }
        }

        Given("올바른 호출자 토큰으로 호출할 때") {
            When("POST /internal/mcp-tokens/verify 를 호출하면") {
                Then("엔드포인트에 도달해 검증 결과를 응답한다 — edge 원격 호출 경로가 열린다") {
                    // 존재하지 않는 토큰이라 valid=false 를 답한다. 중요한 것은 200 으로
                    // **본문에 도달했다**는 사실이다 — 404 로 막히면 2단계 원격 호출이 성립하지 않는다.
                    // 요청 필드명은 컨트롤러 계약(`token`)을 그대로 쓴다 — 이전 테스트는 401 만
                    // 단언해 본문에 닿지 않았고, 그래서 잘못된 필드명이 드러나지 않았다.
                    mockMvc.perform(
                        post("/internal/mcp-tokens/verify")
                            .header(InternalCallHeaders.CALL_TOKEN, CALL_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"token":"mcp_1_probe"}"""),
                    ).andExpect(status().isOk)
                }
            }
        }
    }
}
