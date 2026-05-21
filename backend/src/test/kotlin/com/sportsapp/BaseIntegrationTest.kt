package com.sportsapp

import io.kotest.core.spec.style.BehaviorSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.support.TestPropertySourceUtils
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [BaseIntegrationTest.Initializer::class])
abstract class BaseIntegrationTest : BehaviorSpec() {

    class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(applicationContext: ConfigurableApplicationContext) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                applicationContext,
                "spring.data.mongodb.uri=${mongoContainer.replicaSetUrl}",
                "storage.image.endpoint=http://${minioContainer.host}:${minioContainer.getMappedPort(9000)}",
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
        val mysqlContainer: MySQLContainer<*> = MySQLContainer("mysql:8.0")
            .withDatabaseName("sports")
            .withUsername("test")
            .withPassword("test")
            .also { it.start() }

        val mongoContainer: MongoDBContainer = MongoDBContainer("mongo:7.0")
            .withReuse(true)
            .also { it.start() }

        @Container
        @ServiceConnection
        val redisContainer: GenericContainer<*> = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)
            .also { it.start() }

        val minioContainer: GenericContainer<*> = GenericContainer("minio/minio:latest")
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server", "/data")
            .withReuse(true)
            .also { it.start() }
    }
}
