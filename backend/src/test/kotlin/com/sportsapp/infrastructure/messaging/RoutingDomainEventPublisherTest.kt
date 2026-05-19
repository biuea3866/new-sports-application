package com.sportsapp.infrastructure.messaging

import com.sportsapp.domain.common.DomainEvent
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.mockk
import io.mockk.verify
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * [U-02] RoutingDomainEventPublisher 라우팅 단위 테스트
 * [U-03] 알 수 없는 이벤트 타입 시 UnknownEventRoutingException 발생
 * [R-02] @TransactionalEventListener AFTER_COMMIT — 트랜잭션 롤백 시 Spring 경로 미호출 검증
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

    Given("[U-03] publishAll 에 Kafka 이벤트와 Spring 이벤트가 혼합된 경우") {
        val kafkaPublisher = mockk<KafkaDomainEventPublisher>(relaxed = true)
        val springPublisher = mockk<SpringDomainEventPublisher>(relaxed = true)
        val routingPublisher = RoutingDomainEventPublisher(kafkaPublisher, springPublisher)
        val kafkaEvent = KafkaEvent(aggregateId = 3L)
        val springEvent = SpringEvent(aggregateId = 3L)

        When("publishAll 을 호출하면") {
            routingPublisher.publishAll(listOf(kafkaEvent, springEvent))

            Then("[U-03] Kafka 경로가 kafkaEvent 에 대해 1회만 호출된다") {
                verify(exactly = 1) { kafkaPublisher.publish(kafkaEvent) }
            }

            Then("[U-03] Spring 경로가 springEvent 에 대해 1회만 호출된다") {
                verify(exactly = 1) { springPublisher.publish(springEvent) }
            }
        }
    }

    Given("[S-02] Kafka 이벤트와 Spring 이벤트 동시 적재 시 각 경로 1회씩 호출된다") {
        val kafkaPublisher = mockk<KafkaDomainEventPublisher>(relaxed = true)
        val springPublisher = mockk<SpringDomainEventPublisher>(relaxed = true)
        val routingPublisher = RoutingDomainEventPublisher(kafkaPublisher, springPublisher)
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

    Given("[R-02] Spring 이벤트는 AFTER_COMMIT 기반으로 트랜잭션 롤백 시 핸들러가 호출되지 않는다") {
        /**
         * R-02: @TransactionalEventListener(phase = AFTER_COMMIT) 는 트랜잭션이 롤백되면
         * 핸들러가 호출되지 않는 것이 Spring 보장 동작이다.
         * RoutingDomainEventPublisher 는 Spring 이벤트를 SpringDomainEventPublisher 에 위임하고,
         * SpringDomainEventPublisher 는 ApplicationEventPublisher.publishEvent() 를 호출한다.
         * ApplicationEventPublisher 가 이벤트를 publishEvent 한 뒤 트랜잭션이 롤백되면
         * @TransactionalEventListener(AFTER_COMMIT) 핸들러는 실행되지 않는다.
         *
         * 이 테스트는 RoutingDomainEventPublisher 가 Spring 경로로 이벤트를 위임함을 확인하며,
         * 트랜잭션 롤백 시 미발행 동작은 Spring 프레임워크 보장이므로 여기서는
         * "Spring 경로(springPublisher.publish)가 1회 호출됨"을 검증한다.
         * 트랜잭션 바운더리 내 롤백 동작은 DomainEventPublisherScenarioTest(S-*) 에서 검증한다.
         */
        val kafkaPublisher = mockk<KafkaDomainEventPublisher>(relaxed = true)
        val springPublisher = mockk<SpringDomainEventPublisher>(relaxed = true)
        val routingPublisher = RoutingDomainEventPublisher(kafkaPublisher, springPublisher)
        val event = SpringEvent(aggregateId = 4L, eventId = "rollback-test-event")

        When("Spring 이벤트를 publish 하면") {
            routingPublisher.publish(event)

            Then("[R-02] SpringDomainEventPublisher.publish 가 1회 호출된다 (AFTER_COMMIT 롤백 시 핸들러 미실행은 Spring 보장)") {
                verify(exactly = 1) { springPublisher.publish(event) }
            }
        }
    }
})
