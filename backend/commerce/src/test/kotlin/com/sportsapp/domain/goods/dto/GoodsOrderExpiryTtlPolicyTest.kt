package com.sportsapp.domain.goods.dto

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * [GoodsOrderExpiryTtlPolicy] — `facility-booking`(W1-11c) `BookingExpiryTtlPolicy`와 동일한
 * 불변조건(`readyTtlMinutes > ttlMinutes`)을 이 값 객체 스스로도 `init`에서 재검증한다 —
 * 인접한 동일 타입(Long) 위치 인자가 뒤바뀌어도 이 타입만 보고 컴파일이 통과하는 것을
 * 막기 위함이다.
 */
class GoodsOrderExpiryTtlPolicyTest : BehaviorSpec({

    Given("readyTtlMinutes가 ttlMinutes보다 클 때") {
        Then("정상 생성된다") {
            val policy = GoodsOrderExpiryTtlPolicy(ttlMinutes = 30, readyTtlMinutes = 90)
            policy.ttlMinutes shouldBe 30
            policy.readyTtlMinutes shouldBe 90
        }
    }

    Given("readyTtlMinutes와 ttlMinutes 값이 전도돼(readyTtlMinutes <= ttlMinutes) 생성될 때") {
        Then("IllegalArgumentException을 던진다 — 값 객체 자신이 불변조건을 강제한다") {
            shouldThrow<IllegalArgumentException> {
                GoodsOrderExpiryTtlPolicy(ttlMinutes = 90, readyTtlMinutes = 30)
            }
        }
    }

    Given("readyTtlMinutes가 ttlMinutes와 같을 때 (경계값)") {
        Then("IllegalArgumentException을 던진다") {
            shouldThrow<IllegalArgumentException> {
                GoodsOrderExpiryTtlPolicy(ttlMinutes = 30, readyTtlMinutes = 30)
            }
        }
    }
})
