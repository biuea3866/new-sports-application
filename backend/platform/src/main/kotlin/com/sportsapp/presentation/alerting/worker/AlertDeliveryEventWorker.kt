package com.sportsapp.presentation.alerting.worker

import com.sportsapp.application.notification.dto.SendRawNotificationCommand
import com.sportsapp.application.notification.usecase.SendRawNotificationUseCase
import com.sportsapp.domain.alerting.event.AlertDeliveryReadyEvent
import com.sportsapp.domain.notification.vo.NotificationChannel
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * `AlertDeliveryReadyEvent`를 소비해 notification 도메인(Discord 채널)으로 발송을 브리지한다
 * (TDD.md §Detail Design, ADR-004: 교차 도메인은 이벤트→presentation→application 경로).
 *
 * 이벤트에는 이미 렌더된 title/body가 담겨 있으므로 템플릿 렌더링을 수행하는
 * `SendNotificationUseCase`(sendWithTemplate) 대신, 원본 payload를 그대로 발송하는
 * [SendRawNotificationUseCase]를 사용한다.
 */
@Component
class AlertDeliveryEventWorker(
    private val sendRawNotificationUseCase: SendRawNotificationUseCase,
    @Value("\${alerting.discord.recipient-user-id:1}") private val recipientUserId: Long,
) {
    private val log = LoggerFactory.getLogger(AlertDeliveryEventWorker::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onDeliveryReady(event: AlertDeliveryReadyEvent) {
        log.info("AlertDeliveryEventWorker: dispatching alertId={}", event.aggregateId)
        try {
            sendRawNotificationUseCase.execute(
                SendRawNotificationCommand(
                    userId = recipientUserId,
                    channel = NotificationChannel.DISCORD,
                    templateId = TEMPLATE_ID,
                    payload = mapOf(
                        "_title" to event.title,
                        "_body" to event.body,
                        "source" to event.source.name,
                        "severity" to event.severity.name,
                        "env" to event.env,
                    ),
                )
            )
        } catch (e: Exception) {
            log.error("AlertDeliveryEventWorker: failed to dispatch alertId={}", event.aggregateId, e)
        }
    }

    companion object {
        private const val TEMPLATE_ID = "alerting.discord"
    }
}
