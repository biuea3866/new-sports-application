package com.sportsapp.presentation.booking.scheduler

import com.sportsapp.application.booking.usecase.GenerateSlotsUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.mockk
import java.util.function.Supplier
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * Release Scenario — `facility.autoslot.enabled=false`(기본값)면 [GenerateSlotsScheduler] 빈 자체가
 * 등록되지 않아 대량 슬롯 생성 사고를 막는다. ExternalApiPropertiesConsistencyTest 선례와 동일하게
 * ApplicationContextRunner로 Testcontainers 없이 빠르게 검증한다.
 */
class GenerateSlotsSchedulerFeatureFlagTest : BehaviorSpec({

    fun contextRunner() = ApplicationContextRunner()
        .withBean(GenerateSlotsUseCase::class.java, Supplier { mockk<GenerateSlotsUseCase>(relaxed = true) })
        .withUserConfiguration(GenerateSlotsScheduler::class.java)

    Given("facility.autoslot.enabled=false 설정") {
        When("컨텍스트를 로드하면") {
            Then("GenerateSlotsScheduler 빈이 등록되지 않는다") {
                contextRunner()
                    .withPropertyValues("facility.autoslot.enabled=false")
                    .run { context ->
                        context.getBeanNamesForType(GenerateSlotsScheduler::class.java) shouldHaveSize 0
                    }
            }
        }
    }

    Given("facility.autoslot.enabled=true 설정") {
        When("컨텍스트를 로드하면") {
            Then("GenerateSlotsScheduler 빈이 등록된다") {
                contextRunner()
                    .withPropertyValues("facility.autoslot.enabled=true")
                    .run { context ->
                        context.getBeanNamesForType(GenerateSlotsScheduler::class.java) shouldHaveSize 1
                    }
            }
        }
    }

    Given("facility.autoslot.enabled 설정이 없는 기본 상태") {
        When("컨텍스트를 로드하면") {
            Then("기본값 false로 간주해 GenerateSlotsScheduler 빈이 등록되지 않는다") {
                contextRunner()
                    .run { context ->
                        context.getBeanNamesForType(GenerateSlotsScheduler::class.java) shouldHaveSize 0
                    }
            }
        }
    }
})
