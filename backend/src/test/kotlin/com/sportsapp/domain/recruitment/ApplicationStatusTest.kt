package com.sportsapp.domain.recruitment

import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ApplicationStatusTest : BehaviorSpec({

    Given("ApplicationStatus 전이 규칙") {
        Then("PENDING은 CONFIRMED와 CANCELLED로 전이 가능하다") {
            ApplicationStatus.PENDING.canTransitTo(ApplicationStatus.CONFIRMED) shouldBe true
            ApplicationStatus.PENDING.canTransitTo(ApplicationStatus.CANCELLED) shouldBe true
            ApplicationStatus.PENDING.canTransitTo(ApplicationStatus.REFUNDED) shouldBe false
        }

        Then("CONFIRMED는 CANCELLED로만 전이 가능하다") {
            ApplicationStatus.CONFIRMED.canTransitTo(ApplicationStatus.CANCELLED) shouldBe true
            ApplicationStatus.CONFIRMED.canTransitTo(ApplicationStatus.REFUNDED) shouldBe false
            ApplicationStatus.CONFIRMED.canTransitTo(ApplicationStatus.PENDING) shouldBe false
        }

        Then("CANCELLED는 REFUNDED로만 전이 가능하다 (환불 게이트 확정 후)") {
            ApplicationStatus.CANCELLED.canTransitTo(ApplicationStatus.REFUNDED) shouldBe true
            ApplicationStatus.CANCELLED.canTransitTo(ApplicationStatus.CONFIRMED) shouldBe false
        }

        Then("REFUNDED는 어떤 상태로도 전이할 수 없다") {
            ApplicationStatus.REFUNDED.canTransitTo(ApplicationStatus.CANCELLED) shouldBe false
        }
    }
})
