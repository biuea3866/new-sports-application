package com.sportsapp.domain.common.security

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * 서비스 간 호출을 증명하는 헤더 이름 계약 — 발신은 edge(S2-08), 검증은 모놀리스(S2-07)가
 * 각자 구현하되, 이름만은 여기 하나를 공유해야 두 모듈이 어긋나지 않는다.
 *
 * **이 테스트는 동작이 아니라 리터럴을 동결한다.** 상수를 자기 리터럴과 비교하므로 회귀 탐지력은
 * "누가 헤더 이름을 바꾸면 실패로 알린다"까지다 — 발신·검증이 실제로 같은 이름을 쓰는지에 대한
 * 실효 게이트는 S2-07 의 `InternalIngressGuardTest` 확장이다. 그 게이트가 생기면 이 테스트는
 * 중복이므로 함께 정리한다.
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
