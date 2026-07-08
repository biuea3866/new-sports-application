package com.sportsapp.domain.payment.vo

enum class OrderType(val displayName: String) {
    BOOKING("시설 예약"),
    TICKETING("티켓 예매"),
    GOODS("상품 주문"),
    RECRUITMENT("모집 참가"),
}
