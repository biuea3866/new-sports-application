package com.sportsapp.infrastructure.goods.retry

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class LimitedDropRetryPropertiesTest : BehaviorSpec({

    Given("LimitedDropRetryProperties 기본값") {
        When("별도 설정 없이 생성하면") {
            Then("[FIX-02] maxAttempts 기본값은 20이다") {
                LimitedDropRetryProperties().maxAttempts shouldBe 20
            }
        }
    }

    Given("LimitedDropRetryPropertiesConfig") {
        When("limitedDropRetryProperties 빈 팩토리 메서드를 호출하면") {
            Then("[FIX-02] LimitedDropRetryProperties 인스턴스를 반환한다 (빈 이름은 메서드명으로 고정)") {
                val properties = LimitedDropRetryPropertiesConfig().limitedDropRetryProperties()
                properties.maxAttempts shouldBe 20
            }
        }
    }
})
