package com.sportsapp.infrastructure.messaging

import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.DomainEventPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * 모놀리스 내부 트랜잭션 이벤트 발행 구현체.
 *
 * Spring [ApplicationEventPublisher] 에 위임한다.
 * presentation 레이어의 [@TransactionalEventListener(AFTER_COMMIT)] 핸들러가 수신한다.
 */
@Component
class SpringDomainEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) : DomainEventPublisher {

    override fun publish(event: DomainEvent) {
        applicationEventPublisher.publishEvent(event)
    }
}
