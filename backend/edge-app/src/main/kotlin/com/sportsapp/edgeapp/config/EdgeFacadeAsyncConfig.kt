package com.sportsapp.edgeapp.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * 파사드 전용 스레드풀 (S2-08 ③ — 조립자 공급 계약 인수).
 *
 * `CatalogCompositionService`·`OrderCompositionService` 가 이 빈들을 **이름으로** 주입받는다.
 * 모듈 경계를 넘는 이름 기반 바인딩이라 컴파일도 모듈 테스트도 잡지 못하고 **조립된 컨텍스트에서만**
 * 드러난다 — 그래서 `EdgeApplicationContextLoadTest` 가 두 빈의 존재와 값을 함께 검증한다.
 *
 * 값은 모놀리스(`CatalogAsyncConfig`·`OrderHistoryAsyncConfig`)와 **동일해야 한다.** 풀 크기가
 * 달라지면 부분 저하·타임아웃 특성이 달라져 섀도 응답 비교(S2-06·S2-15)의 전제가 무너진다.
 * B~D 단계 동안 모놀리스도 같은 경로를 서비스하므로 양쪽에 동시에 존재한다 — 모놀리스 쪽 제거는
 * E단계(S2-18)다.
 */
@Configuration
class EdgeFacadeAsyncConfig {

    @Bean("catalogSearchExecutor")
    fun catalogSearchExecutor(): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = CATALOG_CORE_POOL_SIZE
            maxPoolSize = CATALOG_MAX_POOL_SIZE
            queueCapacity = CATALOG_QUEUE_CAPACITY
            setThreadNamePrefix("catalog-search-")
            initialize()
        }

    @Bean("orderHistoryExecutor")
    fun orderHistoryExecutor(): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = ORDER_CORE_POOL_SIZE
            maxPoolSize = ORDER_MAX_POOL_SIZE
            queueCapacity = ORDER_QUEUE_CAPACITY
            setThreadNamePrefix("order-history-")
            initialize()
        }

    companion object {
        const val CATALOG_CORE_POOL_SIZE = 4
        const val CATALOG_MAX_POOL_SIZE = 8
        const val CATALOG_QUEUE_CAPACITY = 50

        const val ORDER_CORE_POOL_SIZE = 4
        const val ORDER_MAX_POOL_SIZE = 8
        const val ORDER_QUEUE_CAPACITY = 100
    }
}
