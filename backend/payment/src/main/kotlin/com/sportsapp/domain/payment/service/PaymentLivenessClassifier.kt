package com.sportsapp.domain.payment.service

import com.sportsapp.domain.payment.dto.PaymentLivenessQueryResult
import com.sportsapp.domain.payment.dto.PaymentLivenessRow
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
 * 5차 재설계: 4차는 느린 TTL의 시간 앵커를 `booking.createdAt`(예약 생성 시각)에 두었으나,
 * `POST /payments/prepare`(`PaymentApiController`)가 **기존 주문에 새 payment 행을 만드는
 * 가동 중 경로**(`mobile/app/payment/new.tsx`가 실제로 호출)라 "결제가 언제 살아났는가"와
 * 무관해졌다 — 70분 전 생성된 예약에 방금 새 READY payment가 생겨도 예약 생성 시각만으로는
 * 오만료됐다. 앵커를 **payment 발급 시각**(`markReady` 시점에 실제로 기록되는 createdAt)으로
 * 옮기고, 이를 [PaymentLivenessQueryResult.liveSince]로 노출한다.
 *
 * 있는 정보(payment 상태·발급 시각)로만 판정한다:
 * - **settled**(COMPLETED): 돈을 받았다 — 절대 만료(취소) 금지.
 * - **live**(READY 또는 COMPLETED): 사용자가 checkoutUrl을 받았거나 결제 완료 — 결제가
 *   시작이라도 됐다는 신호. 방치돼도 짧은 TTL로 만료시키면 결제창에 머무는 사용자를
 *   오만료할 위험이 있으므로, 호출 컨텍스트(booking 등)가 소유한 **느린 TTL**을 live payment의
 *   **발급 시각 중 최댓값**에 적용해서만 만료를 허용한다. 한 주문에 payment가 여러 건인 것은
 *   정상 흐름이므로(장바구니 K1 + 결제 페이지 K2 재개시 등) 최댓값이 아닌 값을 쓰면 "오래된
 *   READY + 방금 READY" 조합에서 판정이 뒤집혀 오만료가 난다.
 * - 그 외(행 없음/PENDING/FAILED/CANCELLED/REFUNDED): 결제가 시작 못 했거나 종결됐다 —
 *   **빠른 TTL**로 만료 허용(호출 컨텍스트가 후보 조회 시점에 이미 booking 자신의 createdAt으로
 *   적용). PG prepare 실패로 즉시 FAILED로 전이되는 케이스(F-A 고립 예약)가 여기 포함된다
 *   (`PaymentDomainService.applyPgResult`).
 *
 * 두 TTL 값 자체는 payment가 갖지 않는다 — "슬롯을 얼마나 붙잡아 둘 것인가"는 주문
 * 컨텍스트의 정책이므로 호출 컨텍스트(예: booking)가 소유한다.
 */
object PaymentLivenessClassifier {

    private val LIVE_STATUSES = setOf(PaymentStatus.READY, PaymentStatus.COMPLETED)

    fun isLive(status: PaymentStatus): Boolean = status in LIVE_STATUSES

    fun isSettled(status: PaymentStatus): Boolean = status == PaymentStatus.COMPLETED

    /**
     * orderId별 live payment의 createdAt **최댓값**을 [PaymentLivenessQueryResult.liveSince]로,
     * settled(COMPLETED) 주문 id 집합을 [PaymentLivenessQueryResult.settledOrderIds]로 산출한다.
     * live payment가 없는 orderId는 liveSince에 포함되지 않는다.
     */
    fun classify(rows: List<PaymentLivenessRow>): PaymentLivenessQueryResult {
        val liveSince = rows.filter { isLive(it.status) }
            .groupBy { it.orderId }
            .mapValues { (_, group) -> group.maxOf { it.createdAt } }
        val settledOrderIds = rows.filter { isSettled(it.status) }
            .map { it.orderId }
            .toSet()
        return PaymentLivenessQueryResult(liveSince = liveSince, settledOrderIds = settledOrderIds)
    }
}
