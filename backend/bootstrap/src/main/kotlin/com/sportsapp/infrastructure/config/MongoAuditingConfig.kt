package com.sportsapp.infrastructure.config

import java.time.ZonedDateTime
import java.util.Optional
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.auditing.DateTimeProvider

@Configuration
@Profile("!test-jpa")
class MongoAuditingConfig {

    @Bean
    fun mongoAuditingDateTimeProvider(): DateTimeProvider =
        DateTimeProvider { Optional.of(ZonedDateTime.now()) }
}
