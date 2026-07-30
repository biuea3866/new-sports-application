package com.sportsapp.domain.featureflag.entity

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class FeatureFlagStatusTest : BehaviorSpec({

    Given("ACTIVE 상태에서 ARCHIVED로 전이를 시도하면") {
        Then("허용된다") {
            FeatureFlagStatus.ACTIVE.canTransitTo(FeatureFlagStatus.ARCHIVED).shouldBeTrue()
        }
    }

    Given("ARCHIVED 상태에서 ARCHIVED로 전이를 시도하면") {
        Then("canTransitTo가 거부한다") {
            FeatureFlagStatus.ARCHIVED.canTransitTo(FeatureFlagStatus.ARCHIVED).shouldBeFalse()
        }
    }

    Given("ARCHIVED 상태에서 ACTIVE로 전이를 시도하면") {
        Then("허용된다") {
            FeatureFlagStatus.ARCHIVED.canTransitTo(FeatureFlagStatus.ACTIVE).shouldBeTrue()
        }
    }

    Given("ACTIVE 상태에서 ACTIVE로 전이를 시도하면") {
        Then("canTransitTo가 거부한다") {
            FeatureFlagStatus.ACTIVE.canTransitTo(FeatureFlagStatus.ACTIVE).shouldBeFalse()
        }
    }

    Given("ACTIVE 상태의 평가 가능 여부를 조회하면") {
        Then("평가 가능하다") {
            FeatureFlagStatus.ACTIVE.isEvaluable().shouldBeTrue()
        }
    }

    Given("ARCHIVED 상태의 평가 가능 여부를 조회하면") {
        Then("평가 불가능하다") {
            FeatureFlagStatus.ARCHIVED.isEvaluable().shouldBeFalse()
        }
    }
})
