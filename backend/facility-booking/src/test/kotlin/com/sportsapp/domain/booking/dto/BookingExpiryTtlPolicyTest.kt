package com.sportsapp.domain.booking.dto

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * [BookingExpiryTtlPolicy] — 6차 재리뷰 p3-2 회귀. `readyTtlMinutes > ttlMinutes` 불변조건을
 * `BookingExpiryProperties`(application 레이어)뿐 아니라 이 값 객체 자신도 `init`에서
 * 재검증해야 한다 — 그렇지 않으면 `BookingExpiryTtlPolicy(60, 15)`처럼 두 값이 전도돼
 * 생성돼도 이 타입만 보고는 컴파일·런타임 모두 통과해, 위치 인자 뒤바뀜을 막으려던 값
 * 객체 도입 목적이 무색해진다.
 */
class BookingExpiryTtlPolicyTest : BehaviorSpec({

    Given("readyTtlMinutes가 ttlMinutes보다 클 때") {
        Then("정상 생성된다") {
            val policy = BookingExpiryTtlPolicy(ttlMinutes = 15, readyTtlMinutes = 60)
            policy.ttlMinutes shouldBe 15
            policy.readyTtlMinutes shouldBe 60
        }
    }

    Given("readyTtlMinutes와 ttlMinutes 값이 전도돼(readyTtlMinutes <= ttlMinutes) 생성될 때") {
        Then("IllegalArgumentException을 던진다 — 값 객체 자신이 불변조건을 강제한다") {
            shouldThrow<IllegalArgumentException> {
                BookingExpiryTtlPolicy(ttlMinutes = 60, readyTtlMinutes = 15)
            }
        }
    }

    Given("readyTtlMinutes가 ttlMinutes와 같을 때 (경계값)") {
        Then("IllegalArgumentException을 던진다") {
            shouldThrow<IllegalArgumentException> {
                BookingExpiryTtlPolicy(ttlMinutes = 15, readyTtlMinutes = 15)
            }
        }
    }
})
