package com.sportsapp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SportsApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<SportsApplication>(*args)
}
