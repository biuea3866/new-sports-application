package com.sportsapp.application.booking.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * W1-11c 4차 재설계 — [BookingExpiryProperties]는 빠른 TTL(ttlMinutes)·느린 TTL
 * (readyTtlMinutes) 두 값을 갖는다. readyTtlMinutes가 ttlMinutes보다 크지 않으면
 * 결제 진행 중(live)인 예약도 빠른 TTL에 의해 오만료될 수 있으므로, 이 불변조건을
 * `require`로 강제해 부팅 시점에 실패시킨다(env 한 줄 오설정으로 가드가 무력화되는 것을
 * 재기동 즉시 차단 — p2-2 재발 방지).
 */
class BookingExpiryPropertiesTest : BehaviorSpec({

    Given("readyTtlMinutes가 ttlMinutes보다 클 때") {
        When("생성하면") {
            val properties = BookingExpiryProperties(ttlMinutes = 15, readyTtlMinutes = 60)

            Then("정상 생성된다") {
                properties.ttlMinutes shouldBe 15
                properties.readyTtlMinutes shouldBe 60
            }
        }
    }

    Given("readyTtlMinutes가 ttlMinutes와 같을 때 (핵심 회귀 — 가드 무력화 방지)") {
        When("생성하면") {
            Then("IllegalArgumentException을 던져 부팅을 실패시킨다") {
                shouldThrow<IllegalArgumentException> {
                    BookingExpiryProperties(ttlMinutes = 15, readyTtlMinutes = 15)
                }
            }
        }
    }

    Given("readyTtlMinutes가 ttlMinutes보다 작을 때 (핵심 회귀 — 가드 무력화 방지)") {
        When("생성하면") {
            Then("IllegalArgumentException을 던져 부팅을 실패시킨다") {
                shouldThrow<IllegalArgumentException> {
                    BookingExpiryProperties(ttlMinutes = 15, readyTtlMinutes = 10)
                }
            }
        }
    }

    Given("기본값으로 생성할 때") {
        When("생성하면") {
            val properties = BookingExpiryProperties()

            Then("readyTtlMinutes(60)가 ttlMinutes(15)보다 커 불변조건을 만족한다") {
                (properties.readyTtlMinutes > properties.ttlMinutes) shouldBe true
            }
        }
    }
})
