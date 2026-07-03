package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.LimitedDropStatsResult
import com.sportsapp.domain.goods.dto.LimitedDropStats
import com.sportsapp.domain.goods.exception.LimitedDropNotFoundException
import com.sportsapp.domain.goods.service.LimitedDropDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

private const val DROP_ID = 7L

class GetLimitedDropStatsUseCaseTest : BehaviorSpec({

    Given("성공·소진거부·시작전거부가 집계된 회차의 통계 조회 요청") {
        val limitedDropDomainService = mockk<LimitedDropDomainService>()
        val useCase = GetLimitedDropStatsUseCase(limitedDropDomainService)
        val stats = LimitedDropStats(
            successCount = 70,
            soldOutRejectCount = 5,
            tooEarlyRejectCount = 7,
        )

        every { limitedDropDomainService.getStats(DROP_ID) } returns stats

        When("execute를 호출하면") {
            val result = useCase.execute(DROP_ID)

            Then("successCount·soldOutRejectCount·tooEarlyRejectCount를 결합한 결과를 반환한다") {
                result shouldBe LimitedDropStatsResult(
                    successCount = 70,
                    soldOutRejectCount = 5,
                    tooEarlyRejectCount = 7,
                )
                verify(exactly = 1) { limitedDropDomainService.getStats(DROP_ID) }
            }
        }
    }

    Given("존재하지 않는 dropId의 통계 조회 요청") {
        val limitedDropDomainService = mockk<LimitedDropDomainService>()
        val useCase = GetLimitedDropStatsUseCase(limitedDropDomainService)

        every { limitedDropDomainService.getStats(DROP_ID) } throws LimitedDropNotFoundException(DROP_ID)

        When("execute를 호출하면") {
            Then("LimitedDropNotFoundException을 그대로 전파한다") {
                shouldThrow<LimitedDropNotFoundException> { useCase.execute(DROP_ID) }
            }
        }
    }

    Given("GetLimitedDropStatsUseCase 클래스") {
        When("생성자 의존을 확인하면") {
            Then("LimitedDropDomainService만 주입받는다") {
                val constructor = GetLimitedDropStatsUseCase::class.constructors.first()
                constructor.parameters shouldHaveSize 1
                constructor.parameters[0].type.classifier shouldBe LimitedDropDomainService::class
            }
        }
    }
})
