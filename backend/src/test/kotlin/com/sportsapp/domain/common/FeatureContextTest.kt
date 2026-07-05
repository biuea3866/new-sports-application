package com.sportsapp.domain.common

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe

class FeatureContextTest : BehaviorSpec({

    Given("FeatureContext.of(userId)로 생성하면") {
        val context = FeatureContext.of(userId = 10L)

        Then("userId가 채워지고 attributes는 비어있다") {
            context.userId shouldBe 10L
            context.attributes.shouldBeEmpty()
        }
    }

    Given("FeatureContext.anonymous()로 생성하면") {
        val context = FeatureContext.anonymous()

        Then("userId는 null이다") {
            context.userId shouldBe null
        }
    }
})
