package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.LimitedDropPurchaseResult
import com.sportsapp.domain.goods.dto.PurchaseLimitedDropCommand
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import com.sportsapp.domain.goods.exception.LimitedDropSoldOutException
import com.sportsapp.domain.goods.service.LimitedDropDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZonedDateTime

private const val DROP_ID = 1L
private const val PRODUCT_ID = 10L
private const val USER_ID = 100L
private const val QUANTITY = 1
private const val IDEMPOTENCY_KEY = "idem-key-1"

class PurchaseLimitedDropUseCaseTest : BehaviorSpec({

    fun command(): PurchaseLimitedDropCommand = PurchaseLimitedDropCommand(
        dropId = DROP_ID,
        userId = USER_ID,
        quantity = QUANTITY,
        idempotencyKey = IDEMPOTENCY_KEY,
    )

    fun openDrop(): LimitedDrop = LimitedDrop.reconstitute(
        productId = PRODUCT_ID,
        openAt = ZonedDateTime.now().minusMinutes(1),
        closeAt = ZonedDateTime.now().plusDays(1),
        limitedQuantity = 100,
        perUserLimit = 2,
        status = LimitedDropStatus.OPEN,
    )

    Given("DomainService가 구매를 승인해 (drop, order) 쌍을 반환하는 상황") {
        val limitedDropDomainService = mockk<LimitedDropDomainService>()
        val useCase = PurchaseLimitedDropUseCase(limitedDropDomainService)
        val drop = openDrop()
        val order = GoodsOrder.create(userId = USER_ID, totalAmount = BigDecimal("1000"), idempotencyKey = IDEMPOTENCY_KEY)

        every { limitedDropDomainService.purchase(command()) } returns (drop to order)

        When("execute를 호출하면") {
            val result = useCase.execute(command())

            Then("domainService.purchase 결과를 LimitedDropPurchaseResult로 변환해 반환한다") {
                result shouldBe LimitedDropPurchaseResult.of(order, drop)
                result.orderId shouldBe order.id
                result.dropId shouldBe drop.id
            }
        }
    }

    Given("DomainService가 LimitedDropSoldOutException을 던지는 상황") {
        val limitedDropDomainService = mockk<LimitedDropDomainService>()
        val useCase = PurchaseLimitedDropUseCase(limitedDropDomainService)

        every { limitedDropDomainService.purchase(command()) } throws LimitedDropSoldOutException(1L)

        When("execute를 호출하면") {
            Then("예외를 그대로 전파한다") {
                shouldThrow<LimitedDropSoldOutException> { useCase.execute(command()) }
            }
        }
    }

    Given("PurchaseLimitedDropUseCase 클래스") {
        When("생성자 의존을 확인하면") {
            Then("LimitedDropDomainService만 주입받는다") {
                val constructor = PurchaseLimitedDropUseCase::class.constructors.first()
                constructor.parameters shouldHaveSize 1
                constructor.parameters[0].type.classifier shouldBe LimitedDropDomainService::class
            }
        }
    }
})
