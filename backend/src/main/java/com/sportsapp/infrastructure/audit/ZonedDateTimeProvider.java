package com.sportsapp.infrastructure.audit;

import java.time.ZonedDateTime;
import java.util.Optional;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.stereotype.Component;

/**
 * Spring Data JPA 감사 타임스탬프를 ZonedDateTime 으로 제공한다.
 * @EnableJpaAuditing(dateTimeProviderRef = "zonedDateTimeProvider") 로 등록.
 *
 * 기본 CurrentDateTimeProvider 는 LocalDateTime 을 반환하므로
 * JpaAuditingBase 의 ZonedDateTime 필드에 적용되지 않는다.
 */
@Component("zonedDateTimeProvider")
public class ZonedDateTimeProvider implements DateTimeProvider {
    @Override
    @SuppressWarnings("unchecked")
    public Optional<java.time.temporal.TemporalAccessor> getNow() {
        return (Optional<java.time.temporal.TemporalAccessor>) (Optional<?>) Optional.of(ZonedDateTime.now());
    }
}
