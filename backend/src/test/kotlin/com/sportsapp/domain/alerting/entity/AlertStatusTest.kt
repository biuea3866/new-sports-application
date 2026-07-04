package com.sportsapp.domain.alerting.entity

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class AlertStatusTest : BehaviorSpec({

    Given("RAISED 상태") {
        When("ANALYZED·FALLBACK로의 전이 가능 여부를 확인하면") {
            Then("둘 다 허용된다") {
                AlertStatus.RAISED.canTransitTo(AlertStatus.ANALYZED) shouldBe true
                AlertStatus.RAISED.canTransitTo(AlertStatus.FALLBACK) shouldBe true
            }
        }

        When("DELIVERED로의 전이 가능 여부를 확인하면") {
            Then("분석 단계를 건너뛴 전이는 거부된다") {
                AlertStatus.RAISED.canTransitTo(AlertStatus.DELIVERED) shouldBe false
            }
        }
    }

    Given("ANALYZED 상태") {
        When("DELIVERED·DELIVERY_FAILED로의 전이 가능 여부를 확인하면") {
            Then("둘 다 허용된다") {
                AlertStatus.ANALYZED.canTransitTo(AlertStatus.DELIVERED) shouldBe true
                AlertStatus.ANALYZED.canTransitTo(AlertStatus.DELIVERY_FAILED) shouldBe true
            }
        }
    }

    Given("FALLBACK 상태") {
        When("DELIVERED·DELIVERY_FAILED로의 전이 가능 여부를 확인하면") {
            Then("둘 다 허용된다") {
                AlertStatus.FALLBACK.canTransitTo(AlertStatus.DELIVERED) shouldBe true
                AlertStatus.FALLBACK.canTransitTo(AlertStatus.DELIVERY_FAILED) shouldBe true
            }
        }
    }

    Given("DELIVERED 상태(종료 상태)") {
        When("어떤 상태로든 전이 가능 여부를 확인하면") {
            Then("모두 거부된다") {
                AlertStatus.DELIVERED.canTransitTo(AlertStatus.ANALYZED) shouldBe false
                AlertStatus.DELIVERED.canTransitTo(AlertStatus.DELIVERED) shouldBe false
                AlertStatus.DELIVERED.canTransitTo(AlertStatus.DELIVERY_FAILED) shouldBe false
            }
        }
    }

    Given("DELIVERY_FAILED 상태(종료 상태)") {
        When("어떤 상태로든 전이 가능 여부를 확인하면") {
            Then("모두 거부된다") {
                AlertStatus.DELIVERY_FAILED.canTransitTo(AlertStatus.DELIVERED) shouldBe false
            }
        }
    }
})
