package com.sportsapp.edgeapp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * edge 독립 실행체의 진입점 (S2-08).
 *
 * `com.sportsapp` 을 스캔해 `edge` 모듈의 컨텍스트(virtualqueue · catalog·order 파사드 · image)와
 * `common` 의 공유 런타임 커널(오류 응답·부하 셰딩·분산 락)을 조립한다. 모놀리스의
 * `SportsApplication` 과 **같은 베이스 패키지를 스캔하지만 서로 다른 실행체**다 — bootstrap 이
 * `:edge-app` 을 의존하지 않으므로 모놀리스 컨텍스트가 이 클래스를 주워 담지 않는다.
 *
 * - `@EnableScheduling` — `AdmissionPumpScheduler`(대기열 입장 펌프)가 틱마다 돈다.
 * - `@EnableAsync` — catalog·order 파사드의 도메인 fan-out 이 executor 로 나간다.
 * - `@EnableJpaAuditing` 은 **넣지 않는다** — edge 는 DataSource 자체가 없다(소유 테이블 0).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
class EdgeApplication

// Spring Boot Kotlin 진입점의 표준 형태다 — 프로세스당 한 번 호출되므로 배열 복사 비용은
// 문제가 되지 않는다. 모놀리스 `SportsApplication` 도 같은 지점에서 같은 억제를 쓴다.
@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<EdgeApplication>(*args)
}
