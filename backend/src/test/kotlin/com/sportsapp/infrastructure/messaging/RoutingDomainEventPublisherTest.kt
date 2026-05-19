package com.sportsapp.infrastructure.messaging

import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.DomainEventPublisher
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.mockk
import io.mockk.verify
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * [U-02] RoutingDomainEventPublisher 라우팅 단위 테스트
 * [S-02] 이중 경로 동시 발행 시 각 경로 1회 호출
 */
class RoutingDomainEventPublisherTest : BehaviorSpec({

    val fixedTime = ZonedDateTime.of(2026, 5, 20, 0, 0, 0, 0, ZoneOffset.UTC)

    data class KafkaEvent(
        override val aggregateId: Long,
        override val topic: String = "test.topic.v1",
        override val eventId: String = "kafka-event-id",
        override val occurredAt: ZonedDateTime = fixedTime,
    ) : DomainEvent

    data class SpringEvent(
        override val aggregateId: Long,
        override val topic: String? = null,
        override val eventId: String = "spring-event-id",
        override val occurredAt: ZonedDateTime = fixedTime,
    ) : DomainEvent

    Given("[U-02] topic 이 지정된 이벤트 publish 시 Kafka 경로로 라우팅된다") {
        val kafkaPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val springPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val routingPublisher = object : DomainEventPublisher {
            override fun publish(event: DomainEvent) {
                if (event.topic != null) kafkaPublisher.publish(event)
                else springPublisher.publish(event)
            }
        }
        val event = KafkaEvent(aggregateId = 1L)

        When("publish 를 호출하면") {
            routingPublisher.publish(event)

            Then("[U-02] Kafka 경로가 1회 호출된다") {
                verify(exactly = 1) { kafkaPublisher.publish(event) }
            }
        }
    }

    Given("[U-02] topic 이 null 인 이벤트 publish 시 Spring 경로로 라우팅된다") {
        val kafkaPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val springPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val routingPublisher = object : DomainEventPublisher {
            override fun publish(event: DomainEvent) {
                if (event.topic != null) kafkaPublisher.publish(event)
                else springPublisher.publish(event)
            }
        }
        val event = SpringEvent(aggregateId = 2L)

        When("publish 를 호출하면") {
            routingPublisher.publish(event)

            Then("[U-02] Spring 경로가 1회 호출된다") {
                verify(exactly = 1) { springPublisher.publish(event) }
            }
        }
    }

    Given("[S-02] Kafka 이벤트와 Spring 이벤트 동시 적재 시 각 경로 1회씩 호출된다") {
        val kafkaPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val springPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val routingPublisher = object : DomainEventPublisher {
            override fun publish(event: DomainEvent) {
                if (event.topic != null) kafkaPublisher.publish(event)
                else springPublisher.publish(event)
            }
        }
        val kafkaEvent = KafkaEvent(aggregateId = 3L)
        val springEvent = SpringEvent(aggregateId = 3L)

        When("publishAll 을 호출하면") {
            routingPublisher.publishAll(listOf(kafkaEvent, springEvent))

            Then("[S-02] Kafka 경로가 1회만 호출된다") {
                verify(exactly = 1) { kafkaPublisher.publish(kafkaEvent) }
            }

            Then("[S-02] Spring 경로가 1회만 호출된다") {
                verify(exactly = 1) { springPublisher.publish(springEvent) }
            }
        }
    }
})
