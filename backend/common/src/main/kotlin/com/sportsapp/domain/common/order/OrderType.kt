package com.sportsapp.domain.common.order

/**
 * 주문 유형 공유 커널 (BE-01 이관: payment.vo → common.order).
 * 근거: 20260708-상품주문-공유상위컨텍스트-tdd.md 방안2.
 *
 * payment(코어)가 domain 레이어(PaymentEvent)에서 이 값을 참조하는데, domain은
 * domain.common만 import 가능하다(ArchUnit R1). payment·4개 주문 도메인(booking/goods/
 * ticketing/recruitment)·order 파사드가 모두 합법적으로 참조 가능한 유일 위치가
 * domain.common이라 공유 커널로 승격했다. 값·displayName·용도는 이관 전과 불변.
 */
enum class OrderType(val displayName: String) {
    BOOKING("시설 예약"),
    TICKETING("티켓 예매"),
    GOODS("상품 주문"),
    RECRUITMENT("모집 참가"),
    ;

    /**
     * 주문 하나에 판매자가 한 명으로 확정되는 유형인가.
     *
     * 예약은 슬롯(시설) 하나, 티켓은 경기 하나, 모집은 모집글 하나에 귀속돼 판매자가 단일하다.
     * 반면 굿즈 주문은 장바구니에 여러 판매자의 상품이 담길 수 있어 결제 한 건이 여러 판매자에
     * 걸친다 — 그래서 결제 총액·PG 거래 식별자를 특정 판매자에게 그대로 보여주면 남의 몫이 샌다.
     */
    fun isSingleSellerOrder(): Boolean = this != GOODS
}
