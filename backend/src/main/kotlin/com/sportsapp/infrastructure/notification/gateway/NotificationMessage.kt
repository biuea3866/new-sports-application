package com.sportsapp.infrastructure.notification.gateway
import com.sportsapp.domain.notification.entity.Notification
/**
 * 발송용 제목/본문 추출. sendWithTemplate 가 payload 에 넣어둔 _title/_body 를 사용하고,
 * 없으면 templateId 를 제목으로 폴백합니다.
 */
object NotificationMessage {
    fun title(notification: Notification): String =
        (notification.payload.data["_title"] as? String)?.takeIf { it.isNotBlank() }
            ?: notification.templateId

    fun body(notification: Notification): String =
        (notification.payload.data["_body"] as? String) ?: ""
}
