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
import java.security.KeyPairGenerator
import java.util.Base64

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [BaseIntegrationTest.Initializer::class])
abstract class BaseIntegrationTest : BehaviorSpec() {

    class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(applicationContext: ConfigurableApplicationContext) {
            val (jwtPublicKeyPem, jwtPrivateKeyPem) = generateJwtRsaKeyPairPem()
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                applicationContext,
                "spring.data.mongodb.uri=${SharedTestContainers.mongo.replicaSetUrl}",
                "spring.data.redis.host=${SharedTestContainers.redis.host}",
                "spring.data.redis.port=${SharedTestContainers.redis.getMappedPort(6379)}",
                "storage.image.endpoint=http://${SharedTestContainers.minio.host}:${SharedTestContainers.minio.getMappedPort(9000)}",
                "storage.image.access-key=minioadmin",
                "storage.image.secret-key=minioadmin",
                "storage.image.bucket=sports-app",
                "storage.image.region=us-east-1",
                // W1-05: JwtTokenProvider의 RS256 공개/사설키. 저장소에 고정 키를 커밋하지 않기 위해
                // 컨텍스트 초기화마다 새로 생성한다 — 개행은 리터럴 "\n"으로 압축한다
                // (JwtTokenProvider#decodePemBody 가 실제 개행·리터럴 "\n" 둘 다 처리한다).
                "app.jwt.public-key=$jwtPublicKeyPem",
                "app.jwt.private-key=$jwtPrivateKeyPem",
            )
        }
    }

    companion object {
        @JvmField
        @Container
        @ServiceConnection
        val mysqlContainer: MySQLContainer<*> = SharedTestContainers.mysql

        @JvmField
        @Container
        @ServiceConnection
        val redisContainer: GenericContainer<*> = SharedTestContainers.redis
    }
}

/**
 * W1-05: 통합 테스트 전용 RSA 키페어(PEM) 생성. 고정 키 파일을 저장소에 두지 않기 위해
 * 컨텍스트 초기화마다 새로 생성한다(티켓 "로컬 개발용 기본 키를 커밋하지 않는다").
 * 개행은 리터럴 `"\n"`으로 압축해 한 줄 인라인 프로퍼티 값으로 넘긴다.
 */
private fun generateJwtRsaKeyPairPem(): Pair<String, String> {
    val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val encoder = Base64.getMimeEncoder(64, byteArrayOf('\n'.code.toByte()))
    val publicKeyPem = "-----BEGIN PUBLIC KEY-----\\n" +
        encoder.encodeToString(keyPair.public.encoded).replace("\n", "\\n") +
        "\\n-----END PUBLIC KEY-----"
    val privateKeyPem = "-----BEGIN PRIVATE KEY-----\\n" +
        encoder.encodeToString(keyPair.private.encoded).replace("\n", "\\n") +
        "\\n-----END PRIVATE KEY-----"
    return publicKeyPem to privateKeyPem
}
