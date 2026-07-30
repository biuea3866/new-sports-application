package com.sportsapp.application.goods.dto

import com.sportsapp.domain.goods.dto.LimitedDropStats
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.ZonedDateTime

class LimitedDropDtoTest : BehaviorSpec({

    Given("생성된 GoodsOrder와 LimitedDrop") {
        Then("LimitedDropPurchaseResult.of는 orderId·dropId·status를 정확히 매핑한다") {
            val order = GoodsOrder.create(
                userId = 1L,
                totalAmount = BigDecimal.TEN,
                idempotencyKey = "idem-key",
            )
            val drop = LimitedDrop.reconstitute(
                productId = 10L,
                openAt = ZonedDateTime.now().minusMinutes(1),
                closeAt = ZonedDateTime.now().plusMinutes(1),
                limitedQuantity = 100,
                perUserLimit = 1,
                status = LimitedDropStatus.OPEN,
            )

            val result = LimitedDropPurchaseResult.of(order, drop)

            result.orderId shouldBe order.id
            result.dropId shouldBe drop.id
            result.status shouldBe GoodsOrderStatus.PENDING
        }
    }

    Given("LimitedDrop과 remaining 수량·상품 가격") {
        Then("LimitedDropView는 dropId·productId·status·openAt·closeAt·remaining·perUserLimit·totalQuantity·price를 결합해 생성된다") {
            val openAt = ZonedDateTime.now().minusMinutes(1)
            val closeAt = ZonedDateTime.now().plusMinutes(1)
            val drop = LimitedDrop.reconstitute(
                productId = 20L,
                openAt = openAt,
                closeAt = closeAt,
                limitedQuantity = 100,
                perUserLimit = 3,
                status = LimitedDropStatus.OPEN,
            )
            val remaining = 42
            val price = BigDecimal("89000")

            val view = LimitedDropView.of(drop, remaining, price)

            view.dropId shouldBe drop.id
            view.productId shouldBe drop.productId
            view.status shouldBe drop.effectiveStatus(remaining)
            view.openAt shouldBe openAt
            view.closeAt shouldBe closeAt
            view.remaining shouldBe remaining
            view.perUserLimit shouldBe drop.perUserLimit
            view.totalQuantity shouldBe drop.limitedQuantity
            view.price shouldBe price
        }
    }

    Given("한정판 회차 개설 요청 시각") {
        Then("CreateLimitedDropCommand의 openAt·closeAt은 ZonedDateTime 타입으로 유지된다") {
            val openAt = ZonedDateTime.now().plusHours(1)
            val closeAt = ZonedDateTime.now().plusHours(2)

            val command = CreateLimitedDropCommand(
                productId = 30L,
                openAt = openAt,
                closeAt = closeAt,
                limitedQuantity = 50,
                perUserLimit = 2,
                ownerUserId = 99L,
            )

            command.openAt shouldBe openAt
            command.closeAt shouldBe closeAt
        }
    }

    Given("도메인 집계 LimitedDropStats") {
        Then("LimitedDropStatsResult.of는 successCount·soldOutRejectCount·tooEarlyRejectCount를 그대로 매핑한다") {
            val stats = LimitedDropStats(
                successCount = 70,
                soldOutRejectCount = 5,
                tooEarlyRejectCount = 7,
            )

            val result = LimitedDropStatsResult.of(stats)

            result.successCount shouldBe 70
            result.soldOutRejectCount shouldBe 5
            result.tooEarlyRejectCount shouldBe 7
        }
    }
})
