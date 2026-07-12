package com.sportsapp.scenario.notification

import com.sportsapp.TestJpaGatewayStubConfig
import com.sportsapp.domain.payment.event.PaymentEvent
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.infrastructure.notification.mysql.NotificationJpaRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.annotation.Import
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.DockerImageName
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test-jpa")
@Import(TestJpaGatewayStubConfig::class)
@org.springframework.test.context.TestPropertySource(properties = [
    "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration," +
        "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration," +
        "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration",
    "spring.kafka.consumer.group-id=notification-kafka-test",
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "spring.kafka.consumer.properties.spring.json.trusted.packages=com.sportsapp.*",
    "spring.flyway.clean-disabled=false",
    "spring.flyway.clean-on-validation-error=true",
])
class NotificationKafkaScenarioTest(
    @Autowired private val notificationJpaRepository: NotificationJpaRepository,
) : BehaviorSpec() {

    companion object {
        @Container
        @ServiceConnection
        val mysqlContainer: MySQLContainer<*> = MySQLContainer("mysql:8.0")
            .withDatabaseName("sports")
            .withUsername("test")
            .withPassword("test")
            .also { it.start() }

        val kafkaContainer: KafkaContainer = KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
        ).also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun kafkaProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers") { kafkaContainer.bootstrapServers }
        }

        private fun <T> buildKafkaTemplate(bootstrapServers: String): KafkaTemplate<String, T> {
            val producerProps = mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
                // 프로덕션 프로듀서(KafkaProducerConfig, ADD_TYPE_INFO_HEADERS=false)와 동일하게 맞춰,
                // 타입 헤더 없이 판별 payload 의 eventType 판별자로 다형 역직렬화되는 실제 경로를 E2E 로 검증한다.
                JsonSerializer.ADD_TYPE_INFO_HEADERS to false,
            )
            return KafkaTemplate(DefaultKafkaProducerFactory(producerProps))
        }

        /** 타입 헤더도, 유효한 JSON 도 아닌 깨진(poison) 레코드를 raw byte 로 직접 발행한다. */
        private fun sendPoisonRecord(bootstrapServers: String, topic: String, key: String) {
            val producerProps = mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java,
            )
            KafkaProducer<String, ByteArray>(producerProps).use { producer ->
                producer.send(ProducerRecord(topic, key, "{이것은-유효한-JSON이-아님".toByteArray())).get()
            }
        }
    }

    init {
        Given("[S-01] event.payment.payment.v1 토픽에 PaymentEvent.Confirmed 를 발행했을 때") {
            val eventId = "payment-scenario-${System.nanoTime()}"
            val userId = 9001L
            val confirmed = PaymentEvent.Confirmed(
                paymentId = 42L,
                orderType = OrderType.BOOKING,
                orderId = 300L,
                recipientUserId = userId,
                amount = 30000L,
                eventId = eventId,
            )
            val kafkaTemplate = buildKafkaTemplate<PaymentEvent>(kafkaContainer.bootstrapServers)

            When("event.payment.payment.v1 에 이벤트를 발행하면") {
                kafkaTemplate.send(
                    PaymentEvent.TOPIC,
                    confirmed.aggregateId.toString(),
                    confirmed,
                ).get()

                Then("[S-01] 5초 내 결제 완료 알림 row 1건이 notifications 테이블에 적재된다") {
                    var found = false
                    val deadline = System.currentTimeMillis() + 10_000L
                    while (System.currentTimeMillis() < deadline) {
                        val rows = notificationJpaRepository.findByEventId(eventId)
                        if (rows != null) {
                            found = true
                            break
                        }
                        TimeUnit.MILLISECONDS.sleep(300)
                    }
                    found shouldBe true
                }
            }
        }

        Given("[S-02] 동일 PaymentEvent.Confirmed 를 2회 발행했을 때 (멱등성)") {
            val eventId = "payment-idempotent-${System.nanoTime()}"
            val userId = 9002L
            val confirmed = PaymentEvent.Confirmed(
                paymentId = 43L,
                orderType = OrderType.BOOKING,
                orderId = 301L,
                recipientUserId = userId,
                amount = 5000L,
                eventId = eventId,
            )
            val kafkaTemplate = buildKafkaTemplate<PaymentEvent>(kafkaContainer.bootstrapServers)

            When("동일 eventId 로 이벤트를 2회 발행하면") {
                repeat(2) {
                    kafkaTemplate.send(
                        PaymentEvent.TOPIC,
                        confirmed.aggregateId.toString(),
                        confirmed,
                    ).get()
                }

                Then("[S-02] 알림 row 는 1건만 적재된다") {
                    TimeUnit.SECONDS.sleep(5)
                    val count = notificationJpaRepository.findAll()
                        .count { it.eventId == eventId }
                    count shouldBe 1
                }
            }
        }

        Given("event.payment.payment.v1 토픽에 깨진(poison) 레코드가 먼저 발행된 상황") {
            val poisonKey = "poison-${System.nanoTime()}"
            sendPoisonRecord(kafkaContainer.bootstrapServers, PaymentEvent.TOPIC, poisonKey)

            val eventId = "payment-after-poison-${System.nanoTime()}"
            val userId = 9003L
            val confirmed = PaymentEvent.Confirmed(
                paymentId = 44L,
                orderType = OrderType.BOOKING,
                orderId = 302L,
                recipientUserId = userId,
                amount = 7000L,
                eventId = eventId,
            )
            val kafkaTemplate = buildKafkaTemplate<PaymentEvent>(kafkaContainer.bootstrapServers)

            When("뒤이어 정상 PaymentEvent.Confirmed 를 발행하면") {
                kafkaTemplate.send(
                    PaymentEvent.TOPIC,
                    confirmed.aggregateId.toString(),
                    confirmed,
                ).get()

                Then("깨진 레코드는 무한 재시도 없이 건너뛰고 이후 정상 이벤트는 알림으로 정상 적재된다") {
                    var found = false
                    val deadline = System.currentTimeMillis() + 15_000L
                    while (System.currentTimeMillis() < deadline) {
                        val rows = notificationJpaRepository.findByEventId(eventId)
                        if (rows != null) {
                            found = true
                            break
                        }
                        TimeUnit.MILLISECONDS.sleep(300)
                    }
                    found shouldBe true
                }
            }
        }
    }
}
