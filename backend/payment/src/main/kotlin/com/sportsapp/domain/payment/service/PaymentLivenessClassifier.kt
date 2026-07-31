package com.sportsapp.domain.payment.service

import com.sportsapp.domain.payment.entity.PaymentStatus

/**
 * 만료 스위퍼(W1-11a~d 공통)가 소비하는 결제 상태 분류 — 순수 함수.
 *
 * 4차 재설계: 이전(3차)에는 `updatedAt`(최근 활동 시각)으로 "지금 결제 진행 중"을 판정했으나,
 * `PaymentDomainService.initiatePg`가 **주문 생성 요청 트랜잭션 안에서** 끝나
 * (`CreateBookingUseCase` 참고 — booking·payment 생성과 PG prepare 호출이 한 요청 안에서
 * 완결된다) 사용자가 PG 결제창에 머무는 동안 payment 행 쓰기가 0건이다. 즉 `updatedAt`은
 * 항상 결제 개시 시각과 같아, 활동 창(activeSince)이 TTL보다 짧아도 항상 "방치됨"으로
 * 오판정돼 가드가 무력화됐다(재발 방지 근거). **서버에는 "사용자가 지금 결제 중인지"를
 * 알려주는 신호가 존재하지 않는다** — PaymentGateway는 prepare 하나뿐이고 상태 조회·webhook
 * 외 갱신 경로가 없다.
 *
 * 따라서 있는 정보(payment 상태 자체)로만 판정한다:
 * - **settled**(COMPLETED): 돈을 받았다 — 절대 만료(취소) 금지.
 * - **live**(READY 또는 COMPLETED): 사용자가 checkoutUrl을 받았거나 결제 완료 — 결제가
 *   시작이라도 됐다는 신호. 방치돼도 짧은 TTL로 만료시키면 결제창에 머무는 사용자를
 *   오만료할 위험이 있으므로, 호출 컨텍스트(booking 등)가 소유한 **느린 TTL**로만 만료를
 *   허용한다.
 * - 그 외(행 없음/PENDING/FAILED/CANCELLED/REFUNDED): 결제가 시작 못 했거나 종결됐다 —
 *   **빠른 TTL**로 만료 허용. F-A(고립 예약)를 정확히 타격하는 케이스가 여기 포함된다
 *   (PG prepare 실패 시 `PaymentDomainService.applyPgResult`가 즉시 FAILED로 전이시킨다).
 *
 * 두 TTL 값 자체는 payment가 갖지 않는다 — "슬롯을 얼마나 붙잡아 둘 것인가"는 주문
 * 컨텍스트의 정책이므로 호출 컨텍스트(예: booking)가 소유한다.
 */
object PaymentLivenessClassifier {

    private val LIVE_STATUSES = setOf(PaymentStatus.READY, PaymentStatus.COMPLETED)

    fun isLive(status: PaymentStatus): Boolean = status in LIVE_STATUSES

    fun isSettled(status: PaymentStatus): Boolean = status == PaymentStatus.COMPLETED
}
