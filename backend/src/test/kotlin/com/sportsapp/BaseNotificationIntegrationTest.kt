package com.sportsapp

import io.kotest.core.spec.style.BehaviorSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.MySQLContainer

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("notification-test")
abstract class BaseNotificationIntegrationTest : BehaviorSpec() {

    companion object {
        @JvmStatic
        @ServiceConnection
        val mysqlContainer: MySQLContainer<*> = SportsTestContainers.mysql
    }
}
