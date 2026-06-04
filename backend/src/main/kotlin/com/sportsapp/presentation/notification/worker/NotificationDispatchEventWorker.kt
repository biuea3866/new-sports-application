package com.sportsapp.presentation.notification

import com.sportsapp.application.notification.DispatchNotificationUseCase
import com.sportsapp.domain.notification.NotificationDispatchRequestedEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Notification 트랜잭션 커밋 후 외부 채널(PUSH/EMAIL/SMS) 발송을 수행하는 이벤트 핸들러.
 *
 * @Transactional 범위 밖에서 gateway.send() 가 실행되도록 AFTER_COMMIT 에 바인딩한다.
 */
@Component
class NotificationDispatchEventWorker(
    private val dispatchNotificationUseCase: DispatchNotificationUseCase,
) {
    private val log = LoggerFactory.getLogger(NotificationDispatchEventWorker::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onDispatchRequested(event: NotificationDispatchRequestedEvent) {
        log.info("NotificationDispatchEventWorker: dispatching notificationId={}", event.notificationId)
        try {
            dispatchNotificationUseCase.execute(event.notificationId)
        } catch (e: Exception) {
            log.error("NotificationDispatchEventWorker: failed to dispatch notificationId={}", event.notificationId, e)
        }
    }
}
