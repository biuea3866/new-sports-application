package com.sportsapp.infrastructure.messaging

import com.sportsapp.domain.common.AbstractDomainEvent
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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * KafkaDomainEventPublisher — N개 이벤트가 모두 발행되고 라우팅 key 가 aggregateId 와 일치한다.
 * topic 이 null 인 내부 이벤트는 Kafka 로 발행되지 않는다.
 */
class KafkaDomainEventPublisherTest : BehaviorSpec({

    data class OrderPlacedEvent(
        override val aggregateId: Long,
        override val topic: String = "order.placed.v1",
        override val eventId: String,
        override val occurredAt: java.time.ZonedDateTime,
    ) : com.sportsapp.domain.common.DomainEvent

    val kafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))

    beforeSpec { kafkaContainer.start() }

    val topicName = "order.placed.v1"

    Given("Testcontainers Kafka 가 실행 중이고 KafkaDomainEventPublisher 가 구성된 상황") {
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
            ConsumerConfig.GROUP_ID_CONFIG to "domain-event-publisher-test-group",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
        )
        val jsonDeserializer = JsonDeserializer(OrderPlacedEvent::class.java).apply {
            addTrustedPackages("com.sportsapp.*")
            setUseTypeMapperForKey(false)
        }
        val consumerFactory = DefaultKafkaConsumerFactory(
            consumerProps,
            StringDeserializer(),
            jsonDeserializer,
        )

        val received = LinkedBlockingQueue<Pair<String?, OrderPlacedEvent>>()
        val containerProperties = ContainerProperties(topicName)
        containerProperties.messageListener = MessageListener<String, OrderPlacedEvent> { record ->
            received.offer(record.key() to record.value())
        }
        val listenerContainer = KafkaMessageListenerContainer(consumerFactory, containerProperties)
        listenerContainer.start()

        val publisher = KafkaDomainEventPublisher(kafkaTemplate)

        When("3개의 OrderPlacedEvent 를 publishAll 로 발행하면") {
            val events = (1L..3L).map { id ->
                OrderPlacedEvent(
                    aggregateId = id,
                    eventId = "event-$id",
                    occurredAt = java.time.ZonedDateTime.of(2026, 5, 20, 0, 0, 0, 0, java.time.ZoneOffset.UTC),
                )
            }
            publisher.publishAll(events)

            Then("3개 이벤트가 모두 Kafka 에 도착하고 라우팅 key 가 aggregateId 와 일치한다") {
                val receivedRecords = mutableListOf<Pair<String?, OrderPlacedEvent>>()
                repeat(3) {
                    val record = received.poll(10, TimeUnit.SECONDS)
                    record.shouldNotBeNull()
                    receivedRecords.add(record)
                }
                receivedRecords.map { it.second.aggregateId }.sorted() shouldBe listOf(1L, 2L, 3L)
                receivedRecords.forEach { (key, event) -> key shouldBe event.aggregateId.toString() }
            }
        }

        When("topic 이 null 인 이벤트를 publish 하면") {
            data class InternalEvent(
                override val aggregateId: Long,
                override val topic: String? = null,
                override val eventId: String = "internal-id",
                override val occurredAt: java.time.ZonedDateTime = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC),
            ) : com.sportsapp.domain.common.DomainEvent

            publisher.publish(InternalEvent(aggregateId = 99L))

            Then("Kafka 에 발행되지 않는다 (received 큐에 추가 이벤트 없음)") {
                val unexpected = received.poll(2, TimeUnit.SECONDS)
                unexpected shouldBe null
            }
        }

        afterSpec { listenerContainer.stop() }
    }
})
