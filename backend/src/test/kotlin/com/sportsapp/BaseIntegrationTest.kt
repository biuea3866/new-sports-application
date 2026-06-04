package com.sportsapp

import io.kotest.core.spec.style.BehaviorSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.support.TestPropertySourceUtils
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [BaseIntegrationTest.Initializer::class])
abstract class BaseIntegrationTest : BehaviorSpec() {

    class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(applicationContext: ConfigurableApplicationContext) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                applicationContext,
                "spring.data.mongodb.uri=${SharedTestContainers.mongo.replicaSetUrl}",
                "storage.image.endpoint=http://${SharedTestContainers.minio.host}:${SharedTestContainers.minio.getMappedPort(9000)}",
                "storage.image.access-key=minioadmin",
                "storage.image.secret-key=minioadmin",
                "storage.image.bucket=sports-app",
                "storage.image.region=us-east-1",
            )
        }
    }

    companion object {
        @Container
        @ServiceConnection
        val mysqlContainer: MySQLContainer<*> = SharedTestContainers.mysql

        @Container
        @ServiceConnection
        val redisContainer: GenericContainer<*> = SharedTestContainers.redis
    }
}
