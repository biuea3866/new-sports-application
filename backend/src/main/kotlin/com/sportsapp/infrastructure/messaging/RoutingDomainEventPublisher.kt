package com.sportsapp.infrastructure.messaging

import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.DomainEventPublisher
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

/**
 * 이벤트의 [DomainEvent.topic] 유무에 따라 Kafka / Spring 경로로 라우팅하는 구현체.
 *
 * - topic non-null → [KafkaDomainEventPublisher] (외부 도메인 통신)
 * - topic null → [SpringDomainEventPublisher] (모놀리스 내부 트랜잭션 이벤트)
 *
 * [@Primary] 를 선언하여 DomainService 가 [DomainEventPublisher] 를 주입받을 때
 * 이 구현체가 자동 선택된다.
 */
@Primary
@Component
class RoutingDomainEventPublisher(
    private val kafkaDomainEventPublisher: KafkaDomainEventPublisher,
    private val springDomainEventPublisher: SpringDomainEventPublisher,
) : DomainEventPublisher {

    override fun publish(event: DomainEvent) {
        if (event.topic != null) kafkaDomainEventPublisher.publish(event)
        else springDomainEventPublisher.publish(event)
    }
}
