package com.sportsapp.scenario

import com.sportsapp.domain.common.AggregateRoot
import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.infrastructure.messaging.KafkaDomainEventPublisher
import com.sportsapp.infrastructure.messaging.RoutingDomainEventPublisher
import com.sportsapp.infrastructure.messaging.SpringDomainEventPublisher
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.ApplicationEventPublisher
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
 * [S-01] Entity 이벤트 적재 → publishAll → Kafka 도착 5초 내 완료
 * [S-02] Kafka + Spring 이벤트 동시 적재 시 각 경로 1회씩만 호출
 */
class DomainEventPublisherScenarioTest : BehaviorSpec({

    data class TicketReservedEvent(
        override val aggregateId: Long,
        override val topic: String = "ticket.reserved.v1",
        override val eventId: String,
        override val occurredAt: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC),
    ) : DomainEvent

    data class InternalNotificationEvent(
        override val aggregateId: Long,
        override val topic: String? = null,
        override val eventId: String,
        override val occurredAt: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC),
    ) : DomainEvent

    class TicketAggregate(private val ticketId: Long) : AggregateRoot() {
        fun reserve() {
            registerEvent(TicketReservedEvent(aggregateId = ticketId, eventId = "reserved-$ticketId"))
        }

        fun reserveWithNotification() {
            registerEvent(TicketReservedEvent(aggregateId = ticketId, eventId = "reserved-$ticketId"))
            registerEvent(InternalNotificationEvent(aggregateId = ticketId, eventId = "notify-$ticketId"))
        }
    }

    val kafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))

    beforeSpec { kafkaContainer.start() }

    val topicName = "ticket.reserved.v1"

    Given("Testcontainers Kafka 와 RoutingDomainEventPublisher 가 준비된 상황") {
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
            ConsumerConfig.GROUP_ID_CONFIG to "scenario-test-group",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
        )
        val jsonDeserializer = JsonDeserializer(TicketReservedEvent::class.java).apply {
            addTrustedPackages("com.sportsapp.*")
            setUseTypeMapperForKey(false)
        }
        val consumerFactory = DefaultKafkaConsumerFactory(
            consumerProps,
            StringDeserializer(),
            jsonDeserializer,
        )
        val received = LinkedBlockingQueue<TicketReservedEvent>()
        val containerProperties = ContainerProperties(topicName)
        containerProperties.messageListener = MessageListener<String, TicketReservedEvent> { record ->
            received.offer(record.value())
        }
        val listenerContainer = KafkaMessageListenerContainer(consumerFactory, containerProperties)
        listenerContainer.start()

        val mockApplicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val kafkaDomainEventPublisher = KafkaDomainEventPublisher(kafkaTemplate)
        val springDomainEventPublisher = SpringDomainEventPublisher(mockApplicationEventPublisher)
        val routingPublisher = RoutingDomainEventPublisher(kafkaDomainEventPublisher, springDomainEventPublisher)

        When("[S-01] Ticket Entity 가 reserve 후 이벤트를 발행하면") {
            val ticket = TicketAggregate(ticketId = 101L)
            ticket.reserve()

            routingPublisher.publishAll(ticket.pullDomainEvents())

            Then("[S-01] Kafka 에 TicketReservedEvent 가 5초 내 도착한다") {
                val event = received.poll(5, TimeUnit.SECONDS)
                event.shouldNotBeNull()
                event.aggregateId shouldBe 101L
            }
        }

        When("[S-02] Ticket 이 Kafka 이벤트와 Spring 이벤트를 동시에 적재하면") {
            val ticket = TicketAggregate(ticketId = 202L)
            ticket.reserveWithNotification()

            val events = ticket.pullDomainEvents()
            routingPublisher.publishAll(events)

            Then("[S-02] Kafka 경로가 1회 호출된다") {
                val kafkaEvent = received.poll(5, TimeUnit.SECONDS)
                kafkaEvent.shouldNotBeNull()
                kafkaEvent.aggregateId shouldBe 202L
            }

            Then("[S-02] Spring 경로(ApplicationEventPublisher)가 1회 호출된다") {
                verify(exactly = 1) {
                    mockApplicationEventPublisher.publishEvent(match<InternalNotificationEvent> { it.aggregateId == 202L })
                }
            }
        }

        afterSpec { listenerContainer.stop() }
    }
})
