package com.sportsapp.domain.notification

import com.sportsapp.domain.common.AbstractDomainEvent

/**
 * Notification 영속 완료 후 외부 채널 발송을 트리거하는 도메인 이벤트.
 *
 * DomainService 가 트랜잭션 내에서 이 이벤트를 발행하고,
 * presentation 레이어의 NotificationDispatchEventWorker 가
 * AFTER_COMMIT 시점에 gateway.send() 를 수행한다.
 */
data class NotificationDispatchRequestedEvent(
    val notificationId: Long,
    override val aggregateId: Long = notificationId,
) : AbstractDomainEvent(aggregateId = notificationId)
