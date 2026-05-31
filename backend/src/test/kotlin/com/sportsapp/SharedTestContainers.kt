package com.sportsapp

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.MySQLContainer

/**
 * 통합 테스트 공용 싱글톤 컨테이너.
 *
 * 베이스 클래스마다 컨테이너를 따로 기동하던 구조를 JVM 당 한 번만 기동해 공유한다.
 * 각 컨테이너는 lazy 로 첫 참조 시 기동하므로, 특정 컨테이너만 쓰는 테스트(예: 순수 JPA)는
 * 필요한 컨테이너만 띄운다. 생명주기는 JVM 전체이며 종료 시 Ryuk 가 회수한다.
 */
object SharedTestContainers {

    val mysql: MySQLContainer<*> by lazy {
        MySQLContainer("mysql:8.0")
            .withDatabaseName("sports")
            .withUsername("test")
            .withPassword("test")
            .also { it.start() }
    }

    val mongo: MongoDBContainer by lazy {
        MongoDBContainer("mongo:7.0")
            .also { it.start() }
    }

    val redis: GenericContainer<*> by lazy {
        GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)
            .also { it.start() }
    }

    val minio: GenericContainer<*> by lazy {
        GenericContainer("minio/minio:latest")
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server", "/data")
            .also { it.start() }
    }
}
