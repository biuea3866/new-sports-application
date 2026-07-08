package com.sportsapp.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
@EnableAsync
class AsyncConfig {

    @Bean("mcpAuditExecutor")
    fun mcpAuditExecutor(): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 4
            maxPoolSize = 16
            queueCapacity = 200
            setThreadNamePrefix("mcp-audit-")
            initialize()
        }
}
