package com.sportsapp.domain.message.event

import com.sportsapp.domain.common.AbstractDomainEvent
import java.time.ZonedDateTime

/**
 * 메시지 저장(커밋) 이후 실시간 브로드캐스트를 트리거하는 in-process 이벤트.
 *
 * topic 을 지정하지 않아 [com.sportsapp.infrastructure.messaging.SpringDomainEventPublisher] 경로로만 발행된다.
 * presentation `MessageBroadcastEventWorker` 가 `@TransactionalEventListener(AFTER_COMMIT)` 로 수신한다.
 */
class MessageSentEvent(
    messageId: Long,
    val roomId: Long,
    val senderId: Long,
    val content: String,
    val sentAt: ZonedDateTime,
) : AbstractDomainEvent(aggregateId = messageId)
