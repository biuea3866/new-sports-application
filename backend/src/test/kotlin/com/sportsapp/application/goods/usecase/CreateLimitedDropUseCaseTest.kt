package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.CreateLimitedDropCommand
import com.sportsapp.application.goods.dto.LimitedDropView
import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.exception.LimitedDropQuantityExceedsStockException
import com.sportsapp.domain.goods.service.LimitedDropDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime

private const val PRODUCT_ID = 10L
private const val OWNER_USER_ID = 500L
private const val LIMITED_QUANTITY = 30
private const val PER_USER_LIMIT = 2
private val PRICE = BigDecimal("50000")

class CreateLimitedDropUseCaseTest : BehaviorSpec({

    fun command(): CreateLimitedDropCommand = CreateLimitedDropCommand(
        productId = PRODUCT_ID,
        openAt = ZonedDateTime.now().plusHours(1),
        closeAt = ZonedDateTime.now().plusHours(3),
        limitedQuantity = LIMITED_QUANTITY,
        perUserLimit = PER_USER_LIMIT,
        ownerUserId = OWNER_USER_ID,
    )

    Given("DomainService가 회차 개설을 승인하는 상황") {
        val limitedDropDomainService = mockk<LimitedDropDomainService>()
        val useCase = CreateLimitedDropUseCase(limitedDropDomainService)
        val requestCommand = command()
        val drop = LimitedDrop.create(
            productId = PRODUCT_ID,
            openAt = requestCommand.openAt,
            closeAt = requestCommand.closeAt,
            limitedQuantity = LIMITED_QUANTITY,
            perUserLimit = PER_USER_LIMIT,
        )

        every {
            limitedDropDomainService.createDrop(
                productId = PRODUCT_ID,
                openAt = requestCommand.openAt,
                closeAt = requestCommand.closeAt,
                limitedQuantity = LIMITED_QUANTITY,
                perUserLimit = PER_USER_LIMIT,
                ownerUserId = OWNER_USER_ID,
            )
        } returns (drop to PRICE)

        When("execute를 호출하면") {
            val result = useCase.execute(requestCommand)

            Then("domainService.createDrop 결과를 LimitedDropView로 변환해 반환한다") {
                result shouldBe LimitedDropView.of(drop, LIMITED_QUANTITY, PRICE)
                result.remaining shouldBe LIMITED_QUANTITY
                result.price shouldBe PRICE
                verify(exactly = 1) {
                    limitedDropDomainService.createDrop(
                        productId = PRODUCT_ID,
                        openAt = requestCommand.openAt,
                        closeAt = requestCommand.closeAt,
                        limitedQuantity = LIMITED_QUANTITY,
                        perUserLimit = PER_USER_LIMIT,
                        ownerUserId = OWNER_USER_ID,
                    )
                }
            }
        }
    }

    Given("DomainService가 LimitedDropQuantityExceedsStockException을 던지는 상황") {
        val limitedDropDomainService = mockk<LimitedDropDomainService>()
        val useCase = CreateLimitedDropUseCase(limitedDropDomainService)
        val requestCommand = command()

        every {
            limitedDropDomainService.createDrop(
                productId = PRODUCT_ID,
                openAt = requestCommand.openAt,
                closeAt = requestCommand.closeAt,
                limitedQuantity = LIMITED_QUANTITY,
                perUserLimit = PER_USER_LIMIT,
                ownerUserId = OWNER_USER_ID,
            )
        } throws LimitedDropQuantityExceedsStockException(PRODUCT_ID, LIMITED_QUANTITY, 10)

        When("execute를 호출하면") {
            Then("예외를 그대로 전파한다") {
                shouldThrow<LimitedDropQuantityExceedsStockException> { useCase.execute(requestCommand) }
            }
        }
    }

    Given("CreateLimitedDropUseCase 클래스") {
        When("생성자 의존을 확인하면") {
            Then("LimitedDropDomainService만 주입받는다") {
                val constructor = CreateLimitedDropUseCase::class.constructors.first()
                constructor.parameters shouldHaveSize 1
                constructor.parameters[0].type.classifier shouldBe LimitedDropDomainService::class
            }
        }
    }
})
