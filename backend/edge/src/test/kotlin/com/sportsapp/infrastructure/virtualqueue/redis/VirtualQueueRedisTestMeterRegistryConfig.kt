package com.sportsapp.infrastructure.virtualqueue.redis

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/**
 * [W1-01d] `edge` 모듈 로컬 경량 컨텍스트에 `MeterRegistry` 를 공급한다.
 *
 * `edge` 는 `spring-boot-starter-actuator` 를 갖지 않아 `MeterRegistry` 빈을 스스로 오토컨픽하지
 * 못한다(`edge/build.gradle.kts` 의 micrometer 주석 — 런타임에는 조립자인 `bootstrap` 이 공급한다는
 * 암묵 계약이며, 2단계 물리 분리 시 `edge` 가 자기 actuator 를 소유해야 한다). 이 패키지의
 * `VirtualQueueStoreImpl` 은 생성자에 `MeterRegistry` 를 요구하므로 테스트가 직접 공급한다
 * (`commerce` 의 `DropReservationStoreImplTest` 선례).
 *
 * 이 패키지에는 중첩 `@SpringBootApplication` 을 가진 테스트가 둘(`VirtualQueueStoreImplTest`·
 * `VirtualQueueLuaScriptsContractTest`) 있고 **서로의 `TestApp` 까지 함께 스캔**한다. 각 `TestApp`
 * 안에 `@Bean` 을 두면 같은 이름의 빈이 두 번 정의돼 `overriding is disabled` 로 기동에 실패한다 —
 * 그래서 공급 지점을 이 단일 클래스로 모은다.
 */
@TestConfiguration
class VirtualQueueRedisTestMeterRegistryConfig {
    @Bean
    fun meterRegistry(): MeterRegistry = SimpleMeterRegistry()
}
