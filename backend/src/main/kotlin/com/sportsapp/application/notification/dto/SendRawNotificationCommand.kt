package com.sportsapp.application.notification.dto
import com.sportsapp.domain.notification.vo.NotificationChannel

/**
 * 이미 렌더된 문구(_title/_body 등)를 담은 payload를 템플릿 렌더링 없이 그대로 발송할 때 사용한다.
 * [com.sportsapp.application.notification.usecase.SendRawNotificationUseCase] 전용 Command.
 */
data class SendRawNotificationCommand(
    val userId: Long,
    val channel: NotificationChannel,
    val templateId: String,
    val payload: Map<String, Any>,
)
