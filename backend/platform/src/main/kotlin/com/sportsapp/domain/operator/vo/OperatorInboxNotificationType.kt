package com.sportsapp.domain.operator.vo

enum class OperatorInboxNotificationType {
    ANOMALY,
    LOW_INVENTORY,
    BOOKING_CONFLICT,
    POLICY_VIOLATION,
    AUTOMATION_FAILURE,

    /**
     * 내 시설에 예약이 접수됨. 기존 5종 중 "신규 예약 접수"에 해당하는 타입이 없어 최소한으로
     * 추가한다 — BOOKING_CONFLICT(예약 충돌)로 대체하면 타입이 사건을 잘못 설명한다.
     */
    BOOKING_RECEIVED,

    /** 내가 주최한 경기의 티켓이 판매(발권)됨. 위와 같은 이유로 추가한다. */
    TICKET_SOLD,
}
