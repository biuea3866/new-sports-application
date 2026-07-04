package com.sportsapp.domain.featureflag.exception

import com.sportsapp.domain.common.ErrorStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class FeatureFlagStatusConflictExceptionTest : BehaviorSpec({

    Given("ARCHIVED 상태의 플래그로 FeatureFlagStatusConflictException을 생성하면") {
        val exception = FeatureFlagStatusConflictException("demo.feature.hello", FeatureFlagStatus.ARCHIVED)

        Then("status는 CONFLICT(409)로 매핑된다") {
            exception.status shouldBe ErrorStatus.CONFLICT
        }

        Then("메시지에 flagKey와 현재 상태가 포함된다") {
            exception.message shouldBe "FeatureFlag(key=demo.feature.hello) cannot be modified from status=ARCHIVED"
        }
    }
})
