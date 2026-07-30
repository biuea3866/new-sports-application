package com.sportsapp.domain.featureflag.exception

import com.sportsapp.domain.common.ErrorStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class DuplicateFeatureFlagKeyExceptionTest : BehaviorSpec({

    Given("이미 존재하는 flagKey로 DuplicateFeatureFlagKeyException을 생성하면") {
        val exception = DuplicateFeatureFlagKeyException("demo.feature.hello")

        Then("status는 BAD_REQUEST(400)로 매핑된다") {
            exception.status shouldBe ErrorStatus.BAD_REQUEST
        }

        Then("메시지에 flagKey가 포함된다") {
            exception.message shouldBe "FeatureFlag(key=demo.feature.hello) already exists"
        }
    }
})
