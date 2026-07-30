package com.sportsapp.infrastructure.audit

import org.springframework.data.auditing.DateTimeProvider
import org.springframework.stereotype.Component
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor
import java.util.Optional

/**
 * JPA auditing (@CreatedDate / @LastModifiedDate) 에 ZonedDateTime(UTC) 을 제공한다.
 *
 * 기본 DateTimeProvider 는 타입 불일치를 유발하므로 ZonedDateTime 을 직접 반환한다.
 * @EnableJpaAuditing(dateTimeProviderRef = "zonedDateTimeProvider") 에 연결한다.
 */
@Component("zonedDateTimeProvider")
class ZonedDateTimeProvider : DateTimeProvider {

    override fun getNow(): Optional<TemporalAccessor> =
        Optional.of(ZonedDateTime.now(ZoneOffset.UTC))
}
