package com.sportsapp.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * catalog 통합검색(BE-07) 전용 bounded executor. 기존 [AsyncConfig]의 `mcpAuditExecutor`와는
 * 무관한 별도 빈이다 — Single Writer per File 원칙상 AsyncConfig 파일 자체는 수정하지 않는다.
 *
 * [com.sportsapp.application.catalog.CatalogCompositionService]가 이 executor로 5개 도메인
 * 조회를 병렬 fan-out하고, 도메인당 300ms 타임아웃을 건다.
 */
@Configuration
class CatalogAsyncConfig {

    @Bean("catalogSearchExecutor")
    fun catalogSearchExecutor(): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 4
            maxPoolSize = 8
            queueCapacity = 50
            setThreadNamePrefix("catalog-search-")
            initialize()
        }
}
