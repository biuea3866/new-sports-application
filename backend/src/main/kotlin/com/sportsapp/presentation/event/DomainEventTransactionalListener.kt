package com.sportsapp.presentation.event

import com.sportsapp.domain.common.DomainEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 트랜잭션 커밋 후 Spring 내부 도메인 이벤트를 처리하는 리스너 예시.
 *
 * presentation 레이어에 위치한다 (외부 이벤트 진입점과 동일 레이어).
 * 실제 처리는 UseCase 를 경유해야 한다.
 *
 * [TransactionPhase.AFTER_COMMIT] 을 사용하므로 트랜잭션 롤백 시 이 메서드는 호출되지 않는다.
 */
@Component
class DomainEventTransactionalListener {

    private val logger = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleDomainEvent(event: DomainEvent) {
        logger.debug("도메인 이벤트 수신: eventId={}, type={}", event.eventId, event::class.simpleName)
    }
}
