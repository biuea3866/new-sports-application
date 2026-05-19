package com.sportsapp.infrastructure.messaging

import com.sportsapp.domain.common.DomainEvent
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.mockk
import io.mockk.verify
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * [U-02] RoutingDomainEventPublisher 단일 이벤트 라우팅 단위 테스트
 * [U-03] 혼합 이벤트(topic 有/無) publishAll 시 각 경로 정확한 횟수 위임
 * [R-02] publishAll N건 혼합 시 kafka/spring 각각 정확한 횟수 위임 검증
 *        (@TransactionalEventListener 실 롤백은 DomainEventPublisherScenarioTest 영역)
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
        val kafkaPublisher = mockk<KafkaDomainEventPublisher>(relaxed = true)
        val springPublisher = mockk<SpringDomainEventPublisher>(relaxed = true)
        val routingPublisher = RoutingDomainEventPublisher(kafkaPublisher, springPublisher)
        val event = KafkaEvent(aggregateId = 1L)

        When("publish 를 호출하면") {
            routingPublisher.publish(event)

            Then("[U-02] Kafka 경로가 1회 호출된다") {
                verify(exactly = 1) { kafkaPublisher.publish(event) }
            }

            Then("[U-02] Spring 경로는 호출되지 않는다") {
                verify(exactly = 0) { springPublisher.publish(any()) }
            }
        }
    }

    Given("[U-02] topic 이 null 인 이벤트 publish 시 Spring 경로로 라우팅된다") {
        val kafkaPublisher = mockk<KafkaDomainEventPublisher>(relaxed = true)
        val springPublisher = mockk<SpringDomainEventPublisher>(relaxed = true)
        val routingPublisher = RoutingDomainEventPublisher(kafkaPublisher, springPublisher)
        val event = SpringEvent(aggregateId = 2L)

        When("publish 를 호출하면") {
            routingPublisher.publish(event)

            Then("[U-02] Spring 경로가 1회 호출된다") {
                verify(exactly = 1) { springPublisher.publish(event) }
            }

            Then("[U-02] Kafka 경로는 호출되지 않는다") {
                verify(exactly = 0) { kafkaPublisher.publish(any()) }
            }
        }
    }

    Given("[U-03] topic 有 이벤트 2건 + topic 無 이벤트 1건 혼합 publishAll") {
        val kafkaPublisher = mockk<KafkaDomainEventPublisher>(relaxed = true)
        val springPublisher = mockk<SpringDomainEventPublisher>(relaxed = true)
        val routingPublisher = RoutingDomainEventPublisher(kafkaPublisher, springPublisher)
        val kafkaEvent1 = KafkaEvent(aggregateId = 10L, eventId = "k-1")
        val kafkaEvent2 = KafkaEvent(aggregateId = 11L, eventId = "k-2")
        val springEvent = SpringEvent(aggregateId = 12L, eventId = "s-1")

        When("publishAll 을 호출하면") {
            routingPublisher.publishAll(listOf(kafkaEvent1, kafkaEvent2, springEvent))

            Then("[U-03] Kafka 경로가 topic 有 이벤트 2건에 대해 각 1회씩 호출된다") {
                verify(exactly = 1) { kafkaPublisher.publish(kafkaEvent1) }
                verify(exactly = 1) { kafkaPublisher.publish(kafkaEvent2) }
            }

            Then("[U-03] Spring 경로가 topic 無 이벤트 1건에 대해 1회 호출된다") {
                verify(exactly = 1) { springPublisher.publish(springEvent) }
            }
        }
    }

    Given("[R-02] topic 有/無 이벤트 혼합 publishAll 시 각 경로 위임 횟수가 정확하다") {
        // @TransactionalEventListener 실 롤백 동작은 Spring 보장이므로 여기서는
        // RoutingDomainEventPublisher 가 각 publisher 에 정확한 횟수만 위임하는지 검증한다.
        // 트랜잭션 롤백 시 AFTER_COMMIT 핸들러 미실행은 DomainEventPublisherScenarioTest(S-*) 에서 검증한다.
        val kafkaPublisher = mockk<KafkaDomainEventPublisher>(relaxed = true)
        val springPublisher = mockk<SpringDomainEventPublisher>(relaxed = true)
        val routingPublisher = RoutingDomainEventPublisher(kafkaPublisher, springPublisher)
        val kafkaEvents = listOf(
            KafkaEvent(aggregateId = 20L, eventId = "k-a"),
            KafkaEvent(aggregateId = 21L, eventId = "k-b"),
        )
        val springEvents = listOf(
            SpringEvent(aggregateId = 22L, eventId = "s-a"),
        )

        When("kafka 2건 + spring 1건을 publishAll 하면") {
            routingPublisher.publishAll(kafkaEvents + springEvents)

            Then("[R-02] Kafka 경로는 정확히 2회 호출된다") {
                verify(exactly = 2) { kafkaPublisher.publish(any()) }
            }

            Then("[R-02] Spring 경로는 정확히 1회 호출된다") {
                verify(exactly = 1) { springPublisher.publish(any()) }
            }
        }
    }
})
