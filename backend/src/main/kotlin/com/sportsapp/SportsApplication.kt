package com.sportsapp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.mongodb.config.EnableMongoAuditing

@SpringBootApplication
@EnableJpaAuditing(dateTimeProviderRef = "zonedDateTimeProvider")
@EnableMongoAuditing
class SportsApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<SportsApplication>(*args)
}
