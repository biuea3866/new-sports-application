package com.sportsapp.infrastructure.config

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.core.task.SyncTaskExecutor

/**
 * mcpAuditExecutor(AsyncConfig.kt:12) 를 Micrometer ExecutorServiceMetrics 로
 * 바인딩했을 때 executor.active / executor.pool.max 메트릭(Prometheus 노출 시
 * executor_active_threads / executor_pool_max_threads)이 등록되는지 검증한다.
 * Spring context 없이 순수 단위 테스트로 검증한다.
 */
class ThreadPoolMetricsConfigTest : BehaviorSpec({

    Given("mcpAuditExecutor 를 ThreadPoolMetricsConfig 로 바인딩하면") {
        val meterRegistry = SimpleMeterRegistry()
        val executor = AsyncConfig().mcpAuditExecutor()

        ThreadPoolMetricsConfig.bindMcpAuditExecutorMetrics(meterRegistry, executor)

        When("executor.active 메트릭을 조회하면") {
            val activeGauge = meterRegistry.find("executor.active")
                .tag("name", "mcp-audit")
                .gauge()

            Then("name=mcp-audit 태그로 메트릭이 등록된다") {
                activeGauge.shouldNotBeNull()
            }
        }

        When("executor.pool.max 메트릭을 조회하면") {
            val maxPoolGauge = meterRegistry.find("executor.pool.max")
                .tag("name", "mcp-audit")
                .gauge()

            Then("AsyncConfig 의 maxPoolSize(16) 값을 그대로 노출한다") {
                val gauge = maxPoolGauge.shouldNotBeNull()
                gauge.value() shouldBe 16.0
            }
        }

        When("executor.pool.core 메트릭을 조회하면") {
            val corePoolGauge = meterRegistry.find("executor.pool.core")
                .tag("name", "mcp-audit")
                .gauge()

            Then("0보다 큰 core pool size 가 노출된다") {
                val gauge = corePoolGauge.shouldNotBeNull()
                gauge.value() shouldBeGreaterThan 0.0
            }
        }

        executor.shutdown()
    }

    Given("mcpAuditExecutor 빈이 ThreadPoolTaskExecutor 가 아닌 TaskExecutor(예: 테스트 환경의 SyncTaskExecutor)로 교체된 경우") {
        val meterRegistry = SimpleMeterRegistry()
        val syncTaskExecutor = SyncTaskExecutor()

        When("bindIfThreadPoolTaskExecutor 로 바인딩을 시도하면") {
            ThreadPoolMetricsConfig.bindIfThreadPoolTaskExecutor(meterRegistry, syncTaskExecutor)

            Then("예외 없이 통과하고 executor 메트릭이 등록되지 않는다") {
                meterRegistry.find("executor.active").meters().shouldBeEmpty()
            }
        }
    }
})
