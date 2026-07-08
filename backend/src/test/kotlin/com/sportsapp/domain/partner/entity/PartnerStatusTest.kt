package com.sportsapp.domain.partner.entity

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class PartnerStatusTest : BehaviorSpec({

    Given("PartnerStatus 전이 규칙") {
        Then("ACTIVE에서 SUSPENDED로는 전이 가능하다") {
            PartnerStatus.ACTIVE.canTransitTo(PartnerStatus.SUSPENDED) shouldBe true
        }

        Then("SUSPENDED에서 ACTIVE로는 전이 가능하다") {
            PartnerStatus.SUSPENDED.canTransitTo(PartnerStatus.ACTIVE) shouldBe true
        }

        Then("ACTIVE에서 ACTIVE로는 전이 불가능하다") {
            PartnerStatus.ACTIVE.canTransitTo(PartnerStatus.ACTIVE) shouldBe false
        }

        Then("SUSPENDED에서 SUSPENDED로는 전이 불가능하다") {
            PartnerStatus.SUSPENDED.canTransitTo(PartnerStatus.SUSPENDED) shouldBe false
        }
    }
})
