package com.sportsapp.domain.partner.entity

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ApiKeyStatusTest : BehaviorSpec({

    Given("ApiKeyStatus 전이 규칙") {
        Then("ACTIVE에서 REVOKED로는 전이 가능하다") {
            ApiKeyStatus.ACTIVE.canTransitTo(ApiKeyStatus.REVOKED) shouldBe true
        }

        Then("REVOKED에서 ACTIVE로는 전이 불가능하다") {
            ApiKeyStatus.REVOKED.canTransitTo(ApiKeyStatus.ACTIVE) shouldBe false
        }

        Then("ACTIVE에서 ACTIVE로는 전이 불가능하다") {
            ApiKeyStatus.ACTIVE.canTransitTo(ApiKeyStatus.ACTIVE) shouldBe false
        }

        Then("REVOKED에서 REVOKED로는 전이 불가능하다") {
            ApiKeyStatus.REVOKED.canTransitTo(ApiKeyStatus.REVOKED) shouldBe false
        }
    }
})
