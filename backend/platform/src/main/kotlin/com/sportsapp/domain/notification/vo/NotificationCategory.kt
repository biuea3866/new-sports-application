package com.sportsapp.domain.notification.vo

/**
 * 알림함에서 사용자에게 보여줄 알림 분류.
 *
 * 저장 컬럼이 아니라 templateId 로부터 파생하는 표시용 분류다 — 템플릿 하나가 곧 하나의
 * 도메인 사건(payment-completed / booking-confirmed / ticket-issued …)이므로 접두사로 판별한다.
 * 미등록 접두사는 [SYSTEM] 으로 수렴시켜, 새 템플릿이 추가돼도 알림함이 깨지지 않게 한다.
 */
enum class NotificationCategory {
    BOOKING,
    PAYMENT,
    EVENT,
    SYSTEM,
    PROMOTION,
    ;

    companion object {
        fun from(templateId: String): NotificationCategory = when {
            templateId.startsWith("payment") -> PAYMENT
            templateId.startsWith("booking") -> BOOKING
            templateId.startsWith("ticket") -> EVENT
            templateId.startsWith("promo") -> PROMOTION
            else -> SYSTEM
        }
    }
}
