package com.sportsapp.infrastructure.goods.retry

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

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
