package com.sportsapp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.core.Ordered
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableJpaAuditing(
    auditorAwareRef = "securityAuditorAware",
    dateTimeProviderRef = "zonedDateTimeProvider",
)
@EnableRetry(order = Ordered.HIGHEST_PRECEDENCE)
@EnableScheduling
@ConfigurationPropertiesScan
class SportsApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<SportsApplication>(*args)
}
