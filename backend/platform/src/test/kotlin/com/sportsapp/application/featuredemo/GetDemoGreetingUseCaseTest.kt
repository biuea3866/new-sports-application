package com.sportsapp.application.featuredemo

import com.sportsapp.application.featuredemo.dto.GetDemoGreetingCommand
import com.sportsapp.application.featuredemo.usecase.GetDemoGreetingUseCase
import com.sportsapp.domain.featuredemo.exception.FeatureDisabledException
import com.sportsapp.domain.featuredemo.service.FeatureDemoDomainService
import com.sportsapp.domain.featuredemo.vo.Greeting
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class GetDemoGreetingUseCaseTest : BehaviorSpec({

    val featureDemoDomainService = mockk<FeatureDemoDomainService>()
    val getDemoGreetingUseCase = GetDemoGreetingUseCase(featureDemoDomainService)

    Given("FeatureDemoDomainService가 Greeting을 반환하는 상태") {
        val greeting = Greeting.of("demo.feature.hello")
        every { featureDemoDomainService.greet(10L) } returns greeting

        When("X-User-Id 10으로 execute를 호출하면") {
            val response = getDemoGreetingUseCase.execute(GetDemoGreetingCommand(userId = 10L))

            Then("도메인 결과와 요청 userId가 담긴 응답을 반환한다") {
                response.message shouldBe greeting.message
                response.flagKey shouldBe greeting.flagKey
                response.userId shouldBe 10L
                response.servedAt shouldBe greeting.servedAt
            }
        }
    }

    Given("X-User-Id 헤더 없이 호출하는 상태") {
        every { featureDemoDomainService.greet(null) } returns Greeting.of("demo.feature.hello")

        When("userId가 null인 Command로 execute를 호출하면") {
            val response = getDemoGreetingUseCase.execute(GetDemoGreetingCommand(userId = null))

            Then("응답의 userId도 null로 채워진다") {
                response.userId shouldBe null
            }
        }
    }

    Given("FeatureDemoDomainService가 FeatureDisabledException을 던지는 상태") {
        every { featureDemoDomainService.greet(99L) } throws FeatureDisabledException("demo.feature.hello")

        When("execute를 호출하면") {
            Then("예외를 그대로 전파한다") {
                shouldThrow<FeatureDisabledException> {
                    getDemoGreetingUseCase.execute(GetDemoGreetingCommand(userId = 99L))
                }
            }
        }
    }
})
