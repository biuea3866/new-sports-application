package com.sportsapp.infrastructure.messaging

import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.DomainEventPublisher
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

/**
 * 외부 도메인 통신용 Kafka 발행 구현체.
 *
 * [DomainEvent.topic] 이 non-null 인 이벤트를 해당 토픽으로 발행한다.
 * topic 이 null 인 이벤트는 발행하지 않는다 (Spring 내부 이벤트 경로).
 */
@Component
class KafkaDomainEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
) : DomainEventPublisher {

    override fun publish(event: DomainEvent) {
        val topic = event.topic ?: return
        kafkaTemplate.send(topic, event.eventId, event)
    }
}
