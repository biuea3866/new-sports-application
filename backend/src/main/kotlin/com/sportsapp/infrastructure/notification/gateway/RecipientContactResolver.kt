package com.sportsapp.infrastructure.notification.gateway
import com.sportsapp.domain.notification.entity.Notification
import com.sportsapp.domain.user.repository.UserRepository
import org.springframework.stereotype.Component

/**
 * 알림 수신처(이메일/전화번호) 해석기.
 * - email: User 테이블(userId)에서 조회
 * - phone: User 에 전화번호 컬럼이 없으므로 알림 payload 의 "phone" 키에서 조회
 */
@Component
class RecipientContactResolver(
    private val userRepository: UserRepository,
) {
    fun emailOf(userId: Long): String? =
        userRepository.findById(userId)?.email

    fun phoneOf(notification: Notification): String? =
        notification.payload.data["phone"] as? String
}
