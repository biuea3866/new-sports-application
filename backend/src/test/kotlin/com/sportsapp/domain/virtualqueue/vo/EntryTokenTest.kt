package com.sportsapp.domain.virtualqueue.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime

class EntryTokenTest : BehaviorSpec({

    Given("expiresAt가 현재 이전인 토큰") {
        val token = EntryToken(raw = "raw-token", expiresAt = ZonedDateTime.now().minusMinutes(1))

        When("isExpired를 호출하면") {
            val result = token.isExpired()

            Then("true를 반환한다") {
                result shouldBe true
            }
        }
    }

    Given("expiresAt가 현재 이후인 토큰") {
        val token = EntryToken(raw = "raw-token", expiresAt = ZonedDateTime.now().plusMinutes(5))

        When("isExpired를 호출하면") {
            val result = token.isExpired()

            Then("false를 반환한다") {
                result shouldBe false
            }
        }
    }
})
