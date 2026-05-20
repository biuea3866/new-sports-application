package com.sportsapp.scenario.payment

import com.sportsapp.domain.payment.events.PaymentCompletedEvent
import com.sportsapp.infrastructure.messaging.KafkaDomainEventPublisher
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * [S-01] payment.completed.v1 페이로드에 paymentId/orderType/orderId/amount/paidAt 5개 필드가 포함된다
 * [S-02] Payment 트랜잭션 커밋 후 토픽에 정확히 1건만 발행된다
 * [S-03] consumer 가 발행 후 5초 내 메시지를 수신한다
 */
class PaymentKafkaScenarioTest : BehaviorSpec({

    val kafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
        .withReuse(true)

    beforeSpec { kafkaContainer.start() }

    val topicCompleted = "payment.completed.v1"

    Given("Testcontainers Kafka 와 KafkaDomainEventPublisher 가 준비된 상황") {
        val bootstrapServers = kafkaContainer.bootstrapServers

        val producerProps = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            JsonSerializer.ADD_TYPE_INFO_HEADERS to false,
        )
        val kafkaTemplate = KafkaTemplate(DefaultKafkaProducerFactory<String, Any>(producerProps))

        val consumerProps = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "payment-kafka-scenario-group",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
        )
        val jsonDeserializer = JsonDeserializer(PaymentCompletedEvent::class.java).apply {
            addTrustedPackages("com.sportsapp.*")
            setUseTypeMapperForKey(false)
        }
        val consumerFactory = DefaultKafkaConsumerFactory(
            consumerProps,
            StringDeserializer(),
            jsonDeserializer,
        )
        val received = LinkedBlockingQueue<PaymentCompletedEvent>()
        val containerProperties = ContainerProperties(topicCompleted)
        containerProperties.messageListener = MessageListener<String, PaymentCompletedEvent> { record ->
            received.offer(record.value())
        }
        val listenerContainer = KafkaMessageListenerContainer(consumerFactory, containerProperties)
        listenerContainer.start()

        val publisher = KafkaDomainEventPublisher(kafkaTemplate)

        When("[S-01][S-02][S-03] PaymentCompletedEvent 를 1건 발행하면") {
            val paidAt = ZonedDateTime.of(2026, 5, 20, 12, 0, 0, 0, ZoneOffset.UTC)
            val event = PaymentCompletedEvent(
                paymentId = 7L,
                orderType = "BOOKING",
                orderId = 100L,
                amount = BigDecimal("30000"),
                paidAt = paidAt,
            )
            publisher.publish(event)

            Then("[S-01][S-03] consumer 가 5초 내 수신하고 페이로드 5개 필드가 정확하다") {
                val receivedEvent = received.poll(5, TimeUnit.SECONDS)
                receivedEvent.shouldNotBeNull()
                receivedEvent.paymentId shouldBe 7L
                receivedEvent.orderType shouldBe "BOOKING"
                receivedEvent.orderId shouldBe 100L
                receivedEvent.amount shouldBe BigDecimal("30000")
                receivedEvent.paidAt shouldBe paidAt
            }

            Then("[S-02] 토픽에 추가 발행 없이 정확히 1건이다") {
                val unexpected = received.poll(2, TimeUnit.SECONDS)
                unexpected shouldBe null
            }
        }

        afterSpec { listenerContainer.stop() }
    }
})
