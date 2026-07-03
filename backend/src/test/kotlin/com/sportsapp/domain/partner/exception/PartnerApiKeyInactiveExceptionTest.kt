package com.sportsapp.domain.partner.exception
import com.sportsapp.domain.partner.entity.ApiKeyStatus

import com.sportsapp.domain.common.ErrorStatus

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class PartnerApiKeyInactiveExceptionTest : BehaviorSpec({

    Given("REVOKED 상태의 키로 PartnerApiKeyInactiveException을 생성하면") {
        val exception = PartnerApiKeyInactiveException(keyId = 1L, status = ApiKeyStatus.REVOKED)

        Then("status는 UNAUTHORIZED(401)로 매핑된다") {
            exception.status shouldBe ErrorStatus.UNAUTHORIZED
        }

        Then("메시지에 keyId와 현재 상태가 포함된다") {
            exception.message shouldBe "PartnerApiKey(id=1) is not active: current status=REVOKED"
        }
    }
})
