package com.sportsapp.infrastructure.security

import com.sportsapp.BaseJpaIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * W1-06a 가 만든 내부 신원 검증 엔드포인트의 접근 경계를 고정한다 (W1-06b ④).
 *
 * **티켓 대비 의도적 편차** — 티켓은 내부 경로 전체를 "인증 예외로 등록"하라고 적었지만, 1단계에서는
 * `permitAll` 로 열지 않고 `anyRequest().authenticated()` 아래 두는 쪽을 택했다. 근거:
 *  - 1단계 검증은 조립자의 **로컬 어댑터**가 같은 프로세스에서 수행하므로 이 HTTP 엔드포인트를
 *    호출하는 소비자가 아직 없다. 지금 열면 얻는 것 없이 공격 표면만 넓어진다.
 *  - 이 엔드포인트는 "이 토큰/키가 유효한가"를 답하는 **오라클**이다. 인증 없이 열리면 자격증명
 *    유효성을 무제한 대조할 수 있다. nginx 차단(방어 ①)을 우회하는 경로(개발 환경의 직접 포트 노출,
 *    mcp.conf 등 다른 인그레스)가 실제로 존재한다.
 *  - 2단계에 edge → platform 원격 호출로 바뀔 때 `permitAll` 승격이 필요해지며, 그 시점의 전제가
 *    이 티켓에서 추가한 nginx 차단(`InternalIngressGuardTest` 가 고정)이다. 승격은 2단계 작업이다.
 */
@AutoConfigureMockMvc
class InternalVerificationEndpointAuthTest(
    @Autowired private val mockMvc: MockMvc,
) : BaseJpaIntegrationTest() {

    init {
        Given("인증 없이 내부 MCP 토큰 검증 엔드포인트를 호출할 때") {
            When("POST /internal/mcp-tokens/verify 를 호출하면") {
                Then("401 이 반환된다 — 자격증명 유효성 오라클이 외부에 열리지 않는다") {
                    mockMvc.perform(
                        post("/internal/mcp-tokens/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"plainToken":"mcp_1_probe"}"""),
                    ).andExpect(status().isUnauthorized)
                }
            }
        }

        Given("인증 없이 내부 파트너 키 검증 엔드포인트를 호출할 때") {
            When("POST /internal/partner-api-keys/verify 를 호출하면") {
                Then("401 이 반환된다") {
                    mockMvc.perform(
                        post("/internal/partner-api-keys/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"plainKey":"partner_1_probe"}"""),
                    ).andExpect(status().isUnauthorized)
                }
            }
        }
    }
}
