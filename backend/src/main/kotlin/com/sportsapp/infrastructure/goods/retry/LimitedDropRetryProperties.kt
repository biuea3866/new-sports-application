package com.sportsapp.infrastructure.goods.retry

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 한정판 구매 재시도 예산(FIX-02 §③) 값 홀더 — `app.limited-drop.retry.max-attempts`.
 *
 * 기본값을 코드에 고정하고 env(`APP_LIMITED_DROP_RETRY_MAX_ATTEMPTS`)로만 재정의한다 —
 * `application.yml`에 키를 추가하지 않는다(같은 wave의 FIX-03이 yml 소유권을 갖는다, 파일 충돌 방지).
 *
 * 서버측 요청 수명이 커넥션 풀 대기(5s, FIX-03)·LB `proxy_read_timeout`(30s)보다 확실히 짧아야
 * 하므로 기존 200회(최악 총 대기 최대 20초 수준)를 20회(약 1.5초 이내, backoff delay=5ms~100ms
 * 기준)로 낮춘다.
 */
class LimitedDropRetryProperties {
    var maxAttempts: Int = 20
}

/**
 * [LimitedDropRetryProperties]를 고정된 빈 이름(`limitedDropRetryProperties`)으로 배선한다.
 *
 * `@ConfigurationPropertiesScan`이 자동 등록하는 빈 이름은 `{prefix}-{FQCN}` 형식이라 점·하이픈
 * 등 특수문자를 포함해, `PurchaseLimitedDropUseCase`의 `@Retryable(maxAttemptsExpression)` SpEL
 * 빈 참조(`@limitedDropRetryProperties`)에서 예측 가능하게 쓸 수 없다 — 여기서 `@Bean` 메서드로
 * 명시 배선해 빈 이름을 메서드명으로 고정한다. 서드파티(프레임워크) 프로퍼티 바인딩 설정이므로
 * `no-bean-config-wiring`의 예외(우리 서비스가 아닌 조건부·프로퍼티 바인딩 설정)에 해당한다.
 */
@Configuration
class LimitedDropRetryPropertiesConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.limited-drop.retry")
    fun limitedDropRetryProperties(): LimitedDropRetryProperties = LimitedDropRetryProperties()
}
