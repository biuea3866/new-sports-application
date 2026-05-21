package com.sportsapp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@SpringBootApplication
@EnableJpaAuditing(
    auditorAwareRef = "securityAuditorAware",
    dateTimeProviderRef = "zonedDateTimeProvider",
)
@ConfigurationPropertiesScan
class SportsApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<SportsApplication>(*args)
}
