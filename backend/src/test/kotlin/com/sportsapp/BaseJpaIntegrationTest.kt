package com.sportsapp

import io.kotest.core.spec.style.BehaviorSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.MySQLContainer

/**
 * MySQL 전용 통합 테스트 베이스.
 * test-jpa 프로파일로 MongoDB / Kafka 관련 빈을 비활성화하여 순수 JPA 테스트에 사용한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test-jpa")
@TestPropertySource(properties = [
    "spring.data.mongodb.auto-index-creation=false",
    "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration," +
        "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration," +
        "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration," +
        "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
])
abstract class BaseJpaIntegrationTest : BehaviorSpec() {

    companion object {
        @JvmStatic
        @ServiceConnection
        val mysqlContainer: MySQLContainer<*> = SportsTestContainers.mysql
    }
}
