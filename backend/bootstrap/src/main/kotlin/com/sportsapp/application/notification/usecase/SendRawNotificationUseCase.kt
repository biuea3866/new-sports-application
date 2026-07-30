package com.sportsapp.application.notification.usecase

import com.sportsapp.application.notification.dto.SendRawNotificationCommand
import com.sportsapp.domain.notification.dto.NotificationResult
import com.sportsapp.domain.notification.service.NotificationDomainService
import com.sportsapp.domain.notification.vo.NotificationPayload
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 템플릿 렌더링 없이 이미 완성된 문구(payload._title/_body 등)를 그대로 발송한다.
 * Grafana/self-check 등 발신 도메인이 문구를 이벤트에 비정규화해 전달하는 경로 전용
 * (TDD.md §DTO 흐름, BE-09 AlertDeliveryEventWorker).
 */
@Service
class SendRawNotificationUseCase(
    private val notificationDomainService: NotificationDomainService,
) {
    @Transactional
    fun execute(command: SendRawNotificationCommand): NotificationResult =
        notificationDomainService.send(
            userId = command.userId,
            channel = command.channel,
            templateId = command.templateId,
            payload = NotificationPayload(command.payload),
        )
}
