package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.LimitedDropView
import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import com.sportsapp.domain.goods.exception.LimitedDropNotFoundException
import com.sportsapp.domain.goods.service.LimitedDropDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

private const val DROP_ID = 7L
private const val PRODUCT_ID = 10L
private const val PER_USER_LIMIT = 2
private const val REMAINING = 42

class GetLimitedDropUseCaseTest : BehaviorSpec({

    fun drop(): LimitedDrop = LimitedDrop.reconstitute(
        productId = PRODUCT_ID,
        openAt = ZonedDateTime.now().minusMinutes(1),
        closeAt = ZonedDateTime.now().plusDays(1),
        limitedQuantity = 100,
        perUserLimit = PER_USER_LIMIT,
        status = LimitedDropStatus.OPEN,
    )

    Given("존재하는 회차의 조회 요청") {
        val limitedDropDomainService = mockk<LimitedDropDomainService>()
        val useCase = GetLimitedDropUseCase(limitedDropDomainService)
        val existingDrop = drop()

        every { limitedDropDomainService.getView(DROP_ID) } returns (existingDrop to REMAINING)

        When("execute를 호출하면") {
            val result = useCase.execute(DROP_ID)

            Then("status·openAt·closeAt·remaining·perUserLimit을 결합한 LimitedDropView를 반환한다") {
                result shouldBe LimitedDropView.of(existingDrop, REMAINING)
                verify(exactly = 1) { limitedDropDomainService.getView(DROP_ID) }
            }
        }
    }

    Given("Redis remaining이 시드되지 않아 null인 회차의 조회 요청") {
        val limitedDropDomainService = mockk<LimitedDropDomainService>()
        val useCase = GetLimitedDropUseCase(limitedDropDomainService)
        val scheduledDrop = drop()

        every { limitedDropDomainService.getView(DROP_ID) } returns (scheduledDrop to null)

        When("execute를 호출하면") {
            val result = useCase.execute(DROP_ID)

            Then("remaining을 limitedQuantity로 채운 LimitedDropView를 반환한다") {
                result shouldBe LimitedDropView.of(scheduledDrop, scheduledDrop.limitedQuantity)
            }
        }
    }

    Given("존재하지 않는 dropId의 조회 요청") {
        val limitedDropDomainService = mockk<LimitedDropDomainService>()
        val useCase = GetLimitedDropUseCase(limitedDropDomainService)

        every { limitedDropDomainService.getView(DROP_ID) } throws LimitedDropNotFoundException(DROP_ID)

        When("execute를 호출하면") {
            Then("LimitedDropNotFoundException을 그대로 전파한다") {
                shouldThrow<LimitedDropNotFoundException> { useCase.execute(DROP_ID) }
            }
        }
    }

    Given("GetLimitedDropUseCase 클래스") {
        When("생성자 의존을 확인하면") {
            Then("LimitedDropDomainService만 주입받는다") {
                val constructor = GetLimitedDropUseCase::class.constructors.first()
                constructor.parameters shouldHaveSize 1
                constructor.parameters[0].type.classifier shouldBe LimitedDropDomainService::class
            }
        }
    }
})
