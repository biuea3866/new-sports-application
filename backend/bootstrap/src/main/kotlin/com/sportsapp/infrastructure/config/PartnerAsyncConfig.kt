package com.sportsapp.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * partner 감사 적재 전용 스레드풀. 등록 요청 P95(300ms)에 감사 I/O가 끼지 않도록
 * 기존 [AsyncConfig]의 mcpAuditExecutor와 분리한다 (ADR-009). 기존 AsyncConfig.kt는 수정하지 않는다.
 * `@EnableAsync`는 AsyncConfig에 이미 선언돼 있어 애플리케이션 컨텍스트 전역에 적용된다.
 */
@Configuration
class PartnerAsyncConfig {

    @Bean("partnerAuditExecutor")
    fun partnerAuditExecutor(): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 4
            maxPoolSize = 16
            queueCapacity = 200
            setThreadNamePrefix("partner-audit-")
            initialize()
        }
}
