package com.sportsapp.presentation.mcp.scheduler

import com.sportsapp.application.mcp.usecase.DetectMcpAnomalyUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * `SportsApplication`에 `@EnableScheduling`이 도입되면서 그간 휴면 상태였던
 * `McpAnomalyScheduler`가 무의식중에 활성화되는 것을 막는다.
 * 프로퍼티(`mcp.anomaly-detection.scheduler-enabled`)가 없으면 기본 비활성 상태를 유지해야 한다.
 *
 * `McpAnomalyScheduler`는 클래스 레벨 `@Component` + `@ConditionalOnProperty`를 직접 검증해야
 * 하므로, `@Bean` 팩토리 메서드가 아니라 클래스 자체를 `withUserConfiguration`에 등록해
 * 조건 평가(ConditionEvaluator)가 실제로 수행되게 한다.
 */
class McpAnomalySchedulerConditionalTest : BehaviorSpec({

    @Configuration
    class UseCaseConfig {
        @Bean
        fun detectMcpAnomalyUseCase(): DetectMcpAnomalyUseCase = mockk(relaxed = true)
    }

    fun contextRunner(vararg properties: String) =
        ApplicationContextRunner()
            .withUserConfiguration(UseCaseConfig::class.java, McpAnomalyScheduler::class.java)
            .withPropertyValues(*properties)

    Given("mcp.anomaly-detection.scheduler-enabled 프로퍼티가 설정되지 않은 기본 상태") {
        When("애플리케이션 컨텍스트를 구성하면") {
            Then("McpAnomalyScheduler 빈이 등록되지 않는다(직전 휴면 상태 보존)") {
                contextRunner().run { context ->
                    context.containsBean("mcpAnomalyScheduler") shouldBe false
                }
            }
        }
    }

    Given("mcp.anomaly-detection.scheduler-enabled=false 로 명시된 상태") {
        When("애플리케이션 컨텍스트를 구성하면") {
            Then("McpAnomalyScheduler 빈이 등록되지 않는다") {
                contextRunner("mcp.anomaly-detection.scheduler-enabled=false").run { context ->
                    context.containsBean("mcpAnomalyScheduler") shouldBe false
                }
            }
        }
    }

    Given("mcp.anomaly-detection.scheduler-enabled=true 로 명시된 상태") {
        When("애플리케이션 컨텍스트를 구성하면") {
            Then("McpAnomalyScheduler 빈이 등록된다") {
                contextRunner("mcp.anomaly-detection.scheduler-enabled=true").run { context ->
                    context.containsBean("mcpAnomalyScheduler") shouldBe true
                }
            }
        }
    }
})
