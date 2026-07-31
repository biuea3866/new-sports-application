package com.sportsapp.domain.featureflag.exception

import com.sportsapp.domain.common.ErrorStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class FeatureFlagNotFoundExceptionTest : BehaviorSpec({

    Given("존재하지 않는 flagKey로 FeatureFlagNotFoundException을 생성하면") {
        val exception = FeatureFlagNotFoundException("demo.feature.hello")

        Then("status는 NOT_FOUND(404)로 매핑된다") {
            exception.status shouldBe ErrorStatus.NOT_FOUND
        }

        Then("메시지에 flagKey가 포함된다") {
            exception.message shouldBe "FeatureFlag(key=demo.feature.hello) not found"
        }
    }
})
