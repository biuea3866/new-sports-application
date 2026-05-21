package com.sportsapp

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

object SportsTestContainers {
    const val REDIS_PORT = 6379

    val mysql: MySQLContainer<*> by lazy {
        MySQLContainer("mysql:8.0")
            .withDatabaseName("sports")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true)
            .apply { start() }
    }

    val mongo: MongoDBContainer by lazy {
        MongoDBContainer("mongo:7.0")
            .withReuse(true)
            .apply { start() }
    }

    val redis: GenericContainer<*> by lazy {
        GenericContainer("redis:7-alpine")
            .withExposedPorts(REDIS_PORT)
            .withReuse(true)
            .apply { start() }
    }

    val kafka: KafkaContainer by lazy {
        KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
            .withReuse(true)
            .apply { start() }
    }
}
