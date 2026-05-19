package com.sportsapp.infrastructure.messaging

import io.kotest.core.spec.style.BehaviorSpec
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
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * [R-01] test.topic.v1 에 DTO 발행 후 consumer 가 동일 DTO 로 역직렬화하여 수신한다
 */
class KafkaRoundtripTest : BehaviorSpec({

    val kafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))

    beforeSpec { kafkaContainer.start() }
    afterSpec { kafkaContainer.stop() }

    data class TestEvent(val id: Long, val occurredAt: ZonedDateTime)

    val topicName = "test.topic.v1"

    Given("Testcontainers Kafka 컨테이너가 실행 중인 상황") {
        val bootstrapServers = kafkaContainer.bootstrapServers

        val producerProps = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            JsonSerializer.ADD_TYPE_INFO_HEADERS to false,
        )
        val producerFactory = DefaultKafkaProducerFactory<String, TestEvent>(producerProps)
        val kafkaTemplate = KafkaTemplate(producerFactory)

        val consumerProps = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "roundtrip-test-group",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
        )
        val jsonDeserializer = JsonDeserializer(TestEvent::class.java).apply {
            addTrustedPackages("com.sportsapp.*")
            setUseTypeMapperForKey(false)
        }
        val consumerFactory = DefaultKafkaConsumerFactory(
            consumerProps,
            StringDeserializer(),
            jsonDeserializer,
        )

        val received = LinkedBlockingQueue<TestEvent>()
        val containerProperties = ContainerProperties(topicName)
        containerProperties.messageListener = MessageListener<String, TestEvent> { record ->
            received.offer(record.value())
        }
        val listenerContainer = KafkaMessageListenerContainer(consumerFactory, containerProperties)
        listenerContainer.start()

        When("test.topic.v1 에 TestEvent DTO 를 발행하면") {
            val event = TestEvent(
                id = 42L,
                occurredAt = ZonedDateTime.of(2026, 5, 19, 12, 0, 0, 0, ZoneOffset.UTC),
            )
            kafkaTemplate.send(topicName, event).get()

            Then("[R-01] Consumer 가 동일 DTO 로 역직렬화하여 수신한다") {
                val receivedEvent = received.poll(10, TimeUnit.SECONDS)
                receivedEvent shouldBe event
            }
        }

        afterSpec { listenerContainer.stop() }
    }
})
