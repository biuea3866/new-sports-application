package com.sportsapp.domain.common.order

/**
 * 주문 참조 — `(주문 유형, 주문 id)` 한 쌍. 공유 커널([OrderType])과 같은 위치에 둔다.
 *
 * payment는 주문 id만 알고 그 주문이 누구 것인지 모른다(공용 컨텍스트가 주문 컨텍스트를
 * 역참조하면 안 된다). 그래서 "판매자의 주문이 무엇인가"는 각 주문 컨텍스트가 답하고,
 * 그 결과를 이 타입으로 payment에 되물어 결제 행을 얻는다.
 *
 * orderId만으로는 유형이 다른 주문끼리 id가 겹쳐 엉뚱한 결제가 섞이므로 유형과 항상 함께 다닌다.
 */
data class OrderRef(
    val orderType: OrderType,
    val orderId: Long,
)
