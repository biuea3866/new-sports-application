package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.service.LimitedDropDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class ReconcileLimitedDropsUseCaseTest : BehaviorSpec({

    Given("활성 회차 대사 요청") {
        val limitedDropDomainService = mockk<LimitedDropDomainService>()
        val useCase = ReconcileLimitedDropsUseCase(limitedDropDomainService)

        every { limitedDropDomainService.reconcileAllActive() } returns Unit

        When("execute를 호출하면") {
            useCase.execute()

            Then("LimitedDropDomainService.reconcileAllActive를 위임 호출한다") {
                verify(exactly = 1) { limitedDropDomainService.reconcileAllActive() }
            }
        }
    }

    Given("ReconcileLimitedDropsUseCase 클래스") {
        When("생성자 의존을 확인하면") {
            Then("LimitedDropDomainService만 주입받는다") {
                val constructor = ReconcileLimitedDropsUseCase::class.constructors.first()
                constructor.parameters shouldHaveSize 1
                constructor.parameters[0].type.classifier shouldBe LimitedDropDomainService::class
            }
        }
    }
})
