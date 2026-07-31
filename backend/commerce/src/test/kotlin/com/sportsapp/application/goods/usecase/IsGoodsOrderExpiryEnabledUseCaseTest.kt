package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.service.GoodsDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

/**
 * [IsGoodsOrderExpiryEnabledUseCase] — goods.expiry.enabled 킬 스위치 판정을
 * [GoodsDomainService.isExpiryEnabled]에 위임한다(부팅 고정 properties가 아니라
 * FeatureFlagEvaluator 런타임 조회).
 */
class IsGoodsOrderExpiryEnabledUseCaseTest : BehaviorSpec({

    Given("플래그가 활성화되어 있을 때") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val useCase = IsGoodsOrderExpiryEnabledUseCase(goodsDomainService)
        every { goodsDomainService.isExpiryEnabled() } returns true

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("true를 반환한다") {
                result shouldBe true
            }
        }
    }

    Given("플래그가 비활성화되어 있을 때(운영 킬 스위치)") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val useCase = IsGoodsOrderExpiryEnabledUseCase(goodsDomainService)
        every { goodsDomainService.isExpiryEnabled() } returns false

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("false를 반환한다") {
                result shouldBe false
            }
        }
    }
})
