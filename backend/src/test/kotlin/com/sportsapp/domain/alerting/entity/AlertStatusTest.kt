package com.sportsapp.domain.alerting.entity

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class AlertStatusTest : BehaviorSpec({

    Given("RAISED 상태") {
        When("ENRICHED로의 전이 가능 여부를 확인하면") {
            Then("허용된다") {
                AlertStatus.RAISED.canTransitTo(AlertStatus.ENRICHED) shouldBe true
            }
        }

        When("DELIVERED로의 전이 가능 여부를 확인하면") {
            Then("원지표 부착 단계를 건너뛴 전이는 거부된다") {
                AlertStatus.RAISED.canTransitTo(AlertStatus.DELIVERED) shouldBe false
            }
        }
    }

    Given("ENRICHED 상태") {
        When("DELIVERED·DELIVERY_FAILED로의 전이 가능 여부를 확인하면") {
            Then("둘 다 허용된다") {
                AlertStatus.ENRICHED.canTransitTo(AlertStatus.DELIVERED) shouldBe true
                AlertStatus.ENRICHED.canTransitTo(AlertStatus.DELIVERY_FAILED) shouldBe true
            }
        }
    }

    Given("DELIVERED 상태(종료 상태)") {
        When("어떤 상태로든 전이 가능 여부를 확인하면") {
            Then("모두 거부된다") {
                AlertStatus.DELIVERED.canTransitTo(AlertStatus.ENRICHED) shouldBe false
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
