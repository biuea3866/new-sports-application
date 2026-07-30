package com.sportsapp.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * order 통합조회(BE-08) 파사드가 4개 코어 DomainService를 병렬 fan-out할 때 쓰는 전용
 * 스레드풀. 기존 [AsyncConfig]의 mcpAuditExecutor·[PartnerAsyncConfig]의 partnerAuditExecutor와
 * 목적이 달라 별도 빈으로 분리한다(고유 빈 이름 — 동일 simple명 @Component 빈 충돌 회피).
 * `@EnableAsync`는 AsyncConfig에 이미 선언돼 있어 애플리케이션 컨텍스트 전역에 적용된다.
 *
 * corePoolSize=4는 4개 도메인 조회를 동시에 fan-out하기 위한 하한이다(TDD "도메인당 300ms
 * 타임아웃" — 풀이 4개 미만이면 일부 조회가 대기하며 순차화돼 타임아웃 예산을 잠식한다).
 */
@Configuration
class OrderHistoryAsyncConfig {

    @Bean("orderHistoryExecutor")
    fun orderHistoryExecutor(): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 4
            maxPoolSize = 8
            queueCapacity = 100
            setThreadNamePrefix("order-history-")
            initialize()
        }
}
