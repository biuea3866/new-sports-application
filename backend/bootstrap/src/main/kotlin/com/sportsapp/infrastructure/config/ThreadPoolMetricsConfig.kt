package com.sportsapp.infrastructure.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ExecutorService

/**
 * mcpAuditExecutor(AsyncConfig.kt:12)는 Micrometer 자동 바인딩 대상이 아닌 커스텀 executor라
 * ExecutorServiceMetrics로 명시 바인딩한다.
 *
 * 노출 메트릭(Prometheus 변환 후): executor_active_threads{name="mcp-audit"},
 * executor_pool_max_threads{name="mcp-audit"} 등.
 *
 * 주입 타입은 ThreadPoolTaskExecutor 가 아닌 TaskExecutor(느슨한 상위 타입)로 받는다 —
 * 테스트 컨텍스트가 BeanDefinitionRegistryPostProcessor 로 mcpAuditExecutor 빈을
 * SyncTaskExecutor 등 다른 TaskExecutor 구현체로 교체하는 경우(McpAuditInterceptorScenarioTest)
 * 타입 불일치로 컨텍스트 로딩이 깨지는 것을 방지한다.
 */
@Configuration
class ThreadPoolMetricsConfig {

    @Bean
    fun mcpAuditExecutorMetricsBinding(
        meterRegistry: MeterRegistry,
        @Qualifier(MCP_AUDIT_EXECUTOR_BEAN_NAME) mcpAuditExecutor: TaskExecutor,
    ): Any = bindIfThreadPoolTaskExecutor(meterRegistry, mcpAuditExecutor)

    companion object {
        const val MCP_AUDIT_EXECUTOR_BEAN_NAME = "mcpAuditExecutor"
        const val MCP_AUDIT_EXECUTOR_METRIC_NAME = "mcp-audit"

        fun bindIfThreadPoolTaskExecutor(
            meterRegistry: MeterRegistry,
            taskExecutor: TaskExecutor,
        ): Any =
            when (taskExecutor) {
                is ThreadPoolTaskExecutor -> bindMcpAuditExecutorMetrics(meterRegistry, taskExecutor)
                else -> Unit
            }

        fun bindMcpAuditExecutorMetrics(
            meterRegistry: MeterRegistry,
            executor: ThreadPoolTaskExecutor,
        ): ExecutorService =
            ExecutorServiceMetrics.monitor(
                meterRegistry,
                executor.threadPoolExecutor,
                MCP_AUDIT_EXECUTOR_METRIC_NAME,
            )
    }
}
