package com.sportsapp.infrastructure.audit

import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.TemporalAccessor
import java.util.Optional
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.stereotype.Component

/**
 * JPA Auditing 에서 @CreatedDate / @LastModifiedDate 가 ZonedDateTime 에 채워지도록
 * UTC ZonedDateTime 을 반환하는 DateTimeProvider.
 *
 * @EnableJpaAuditing(dateTimeProviderRef = "zonedDateTimeProvider") 로 등록.
 */
@Component("zonedDateTimeProvider")
class ZonedDateTimeProvider : DateTimeProvider {

    override fun getNow(): Optional<TemporalAccessor> =
        Optional.of(ZonedDateTime.now(ZoneOffset.UTC))
}
