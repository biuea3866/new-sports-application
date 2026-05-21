package com.sportsapp

import io.kotest.core.spec.style.BehaviorSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.support.TestPropertySourceUtils
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.MySQLContainer

/**
 * Mongo + MySQL 통합 테스트 베이스.
 * MongoInitializer 를 통해 컨테이너 URI 를 Spring 컨텍스트에 주입한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [BaseMongoIntegrationTest.MongoInitializer::class])
abstract class BaseMongoIntegrationTest : BehaviorSpec() {

    class MongoInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(applicationContext: ConfigurableApplicationContext) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                applicationContext,
                "spring.data.mongodb.uri=${SportsTestContainers.mongo.replicaSetUrl}",
            )
        }
    }

    companion object {
        @JvmStatic
        @ServiceConnection
        val mysqlContainer: MySQLContainer<*> = SportsTestContainers.mysql

        @JvmStatic
        val mongoContainer: MongoDBContainer = SportsTestContainers.mongo
    }
}
