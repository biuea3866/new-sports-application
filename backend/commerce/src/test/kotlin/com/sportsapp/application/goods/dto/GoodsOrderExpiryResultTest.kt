package com.sportsapp.application.goods.dto

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * [GoodsOrderExpiryResult] — W1-11a 만료 스위퍼 1회 실행(또는 청크 1건) 결과 누적.
 * `facility-booking`(W1-11c)의 `BookingExpiryResult`와 동일 구조 — expiredCount/skippedCount/
 * skippedSettledCount/contendedCount를 [plus]로 누적한다.
 */
class GoodsOrderExpiryResultTest : BehaviorSpec({

    Given("두 결과를 합칠 때") {
        val first = GoodsOrderExpiryResult(expiredCount = 2, skippedCount = 1, skippedSettledCount = 1, contendedCount = 0)
        val second = GoodsOrderExpiryResult(expiredCount = 3, skippedCount = 0, skippedSettledCount = 0, contendedCount = 1)

        When("plus를 호출하면") {
            val merged = first + second

            Then("각 필드가 합산된다") {
                merged.expiredCount shouldBe 5
                merged.skippedCount shouldBe 1
                merged.skippedSettledCount shouldBe 1
                merged.contendedCount shouldBe 1
            }
        }
    }

    Given("empty() 팩토리를 호출할 때") {
        Then("모든 필드가 0이다") {
            val empty = GoodsOrderExpiryResult.empty()
            empty.expiredCount shouldBe 0
            empty.skippedCount shouldBe 0
            empty.skippedSettledCount shouldBe 0
            empty.contendedCount shouldBe 0
        }
    }
})
