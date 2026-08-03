package com.sportsapp.domain.common.security

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * 서비스 간 호출을 증명하는 헤더 이름 계약 — 발신은 edge(S2-08), 검증은 모놀리스(S2-07)가
 * 각자 구현하되, 이름만은 여기 하나를 공유해야 두 모듈이 어긋나지 않는다.
 */
class InternalCallHeadersTest : BehaviorSpec({

    Given("내부 호출 신뢰 헤더 계약") {
        When("호출자 인증용 헤더 이름을 조회하면") {
            Then("X-Internal-Call-Token 을 반환한다") {
                InternalCallHeaders.CALL_TOKEN shouldBe "X-Internal-Call-Token"
            }
        }
    }
})
