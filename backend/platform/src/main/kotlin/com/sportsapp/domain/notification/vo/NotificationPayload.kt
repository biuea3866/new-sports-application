package com.sportsapp.domain.notification.vo

import java.util.Locale

/**
 * 알림 템플릿에 채워 넣을 변수 묶음.
 *
 * 변수 구성은 팩토리로만 만든다 — 호출부가 맵을 직접 조립하면 같은 변수의 표기 규칙이 발행 지점마다
 * 갈린다. 실제로 결제 금액을 `Long.toString()` 으로 실어 알림 본문이
 * `88000원 결제가 완료되었습니다.` 로 찍혔고, 같은 앱의 장바구니(`총 280,000원`)·티켓 주문 확인
 * (`88,000원`)과 표기가 어긋났다(01-모바일앱/36 캡쳐).
 */
data class NotificationPayload(
    val data: Map<String, Any>,
) {
    companion object {
        /** 채울 변수가 없는 알림(예약 확정 등). */
        fun empty(): NotificationPayload = NotificationPayload(emptyMap())

        /**
         * 결제 완료 알림 변수.
         *
         * 금액은 사용자에게 보이는 문장에 그대로 들어가므로 천 단위 구분자를 넣는다.
         * 구분자가 실행 환경 기본 로케일에 따라 달라지지 않도록(예: de-DE 는 `.`)
         * [Locale.KOREA] 를 명시한다.
         */
        fun paymentCompleted(amount: Long): NotificationPayload =
            NotificationPayload(mapOf("amount" to String.format(Locale.KOREA, "%,d", amount)))

        /** 티켓 발권·판매 알림 변수. */
        fun ticketIssued(eventTitle: String): NotificationPayload =
            NotificationPayload(mapOf("eventTitle" to eventTitle))
    }
}
