package com.sportsapp.domain.featureflag.exception

import com.sportsapp.domain.common.ErrorStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class InvalidEvaluationStrategyExceptionTest : BehaviorSpec({

    Given("유효하지 않은 전략 사유로 InvalidEvaluationStrategyException을 생성하면") {
        val exception = InvalidEvaluationStrategyException("percentage must be within 0..100, got 101")

        Then("status는 BAD_REQUEST(400)로 매핑된다") {
            exception.status shouldBe ErrorStatus.BAD_REQUEST
        }

        Then("메시지는 전달된 사유 그대로다") {
            exception.message shouldBe "percentage must be within 0..100, got 101"
        }
    }
})
