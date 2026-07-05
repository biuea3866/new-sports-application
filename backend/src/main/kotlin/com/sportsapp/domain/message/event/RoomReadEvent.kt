package com.sportsapp.domain.message.event

import com.sportsapp.domain.common.AbstractDomainEvent

/**
 * 읽음 커서 갱신(커밋) 이후 실시간 브로드캐스트를 트리거하는 in-process 이벤트 (BE-05 정정).
 *
 * topic 을 지정하지 않아 [com.sportsapp.infrastructure.messaging.SpringDomainEventPublisher] 경로로만 발행된다.
 * presentation `RoomReadEventWorker` 가 `@TransactionalEventListener(AFTER_COMMIT)` 로 수신한다.
 */
class RoomReadEvent(
    roomId: Long,
    val userId: Long,
    val lastReadMessageId: Long,
) : AbstractDomainEvent(aggregateId = roomId) {
    val roomId: Long get() = aggregateId
}
