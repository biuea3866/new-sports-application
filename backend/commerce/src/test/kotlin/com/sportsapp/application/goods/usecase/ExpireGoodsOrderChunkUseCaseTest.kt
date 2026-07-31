package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.service.GoodsDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/**
 * [ExpireGoodsOrderChunkUseCase] — W1-11a 만료 스위퍼의 청크 단위 트랜잭션 경계.
 * `facility-booking`(W1-11c)의 `ExpireBookingChunkUseCase`와 동일한 이유로 `@Transactional`을
 * UseCase에 선언하고, 별도 빈이라 [ExpirePendingGoodsOrdersUseCase]의 청크 루프에서
 * self-invocation 없이 호출된다.
 */
class ExpireGoodsOrderChunkUseCaseTest : BehaviorSpec({

    Given("만료 대상 id 목록이 주어졌을 때") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val useCase = ExpireGoodsOrderChunkUseCase(goodsDomainService)
        every { goodsDomainService.expireOrders(listOf(1L, 2L)) } returns 2

        When("execute를 호출하면") {
            val result = useCase.execute(listOf(1L, 2L))

            Then("GoodsDomainService.expireOrders에 위임하고 결과를 그대로 반환한다") {
                result shouldBe 2
                verify(exactly = 1) { goodsDomainService.expireOrders(listOf(1L, 2L)) }
            }
        }
    }

    Given("빈 목록이 주어졌을 때") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val useCase = ExpireGoodsOrderChunkUseCase(goodsDomainService)
        every { goodsDomainService.expireOrders(emptyList()) } returns 0

        When("execute를 호출하면") {
            val result = useCase.execute(emptyList())

            Then("0을 반환한다") {
                result shouldBe 0
            }
        }
    }
})
