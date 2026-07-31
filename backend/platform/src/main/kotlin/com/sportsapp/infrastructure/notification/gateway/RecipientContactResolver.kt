package com.sportsapp.infrastructure.notification.gateway
import com.sportsapp.domain.notification.entity.Notification
import com.sportsapp.domain.notification.gateway.RecipientContactGateway
import org.springframework.stereotype.Component

/**
 * 알림 수신처(이메일/전화번호) 해석기.
 * - email: notification 의 ACL 인 [RecipientContactGateway] 에 위임한다. user 컨텍스트의
 *   저장소(`users` 테이블)를 직접 알지 않는다.
 * - phone: User 에 전화번호 컬럼이 없으므로 알림 payload 의 "phone" 키에서 조회한다.
 *   notification 이 자기 payload 를 읽는 것이라 크로스 컨텍스트 조회가 아니다.
 */
@Component
class RecipientContactResolver(
    private val recipientContactGateway: RecipientContactGateway,
) {
    fun emailOf(userId: Long): String? =
        recipientContactGateway.emailOf(userId)

    fun phoneOf(notification: Notification): String? =
        notification.payload.data["phone"] as? String
}
