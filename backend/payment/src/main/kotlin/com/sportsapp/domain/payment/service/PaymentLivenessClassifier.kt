package com.sportsapp.domain.payment.service

import com.sportsapp.domain.common.payment.OrderPaymentLiveness
import com.sportsapp.domain.payment.dto.PaymentLivenessQueryResult
import com.sportsapp.domain.payment.dto.PaymentLivenessRow
import com.sportsapp.domain.payment.entity.PaymentStatus

/**
 * 만료 스위퍼(W1-11a~d 공통)가 소비하는 결제 상태 분류 — 순수 함수.
 *
 * 4차 재설계: 이전(3차)에는 `updatedAt`(최근 활동 시각)으로 "지금 결제 진행 중"을 판정했으나,
 * `PaymentDomainService.initiatePg`가 **PG 왕복(외부 호출) 동안은 어떤 트랜잭션도 열려있지
 * 않아** (`createPending`이 먼저 커밋하고, PG 응답 후 별도 트랜잭션에서 `applyPgResult`가
 * 갱신한다 — 아래 6차 참고) payment 행 쓰기가 그 사이 0건이다. 즉 `updatedAt`은 항상 결제
 * 개시 시각과 같아, 활동 창(activeSince)이 TTL보다 짧아도 항상 "방치됨"으로 오판정돼 가드가
 * 무력화됐다(재발 방지 근거). **서버에는 "사용자가 지금 결제 중인지"를 알려주는 신호가
 * 존재하지 않는다** — PaymentGateway는 prepare 하나뿐이고 상태 조회·webhook 외 갱신 경로가
 * 없다.
 *
 * 5차 재설계: 4차는 느린 TTL의 시간 앵커를 `booking.createdAt`(예약 생성 시각)에 두었으나,
 * `POST /payments/prepare`(`PaymentApiController`)가 **기존 주문에 새 payment 행을 만드는
 * 가동 중 경로**(`mobile/app/payment/new.tsx`가 실제로 호출)라 "결제가 언제 살아났는가"와
 * 무관해졌다 — 70분 전 생성된 예약에 방금 새 READY payment가 생겨도 예약 생성 시각만으로는
 * 오만료됐다. 앵커를 **payment 발급 시각**으로 옮기고, 이를 [OrderPaymentLiveness.Live.since]로
 * 노출한다.
 *
 * **6차 재설계 (p1 — 빠른 TTL 갈래의 잔여 앵커 결함)**: 5차는 느린 TTL(live) 갈래만 앵커를
 * 옮겼고, 빠른 TTL 갈래(live payment가 없는 경우)는 여전히 앵커가 없어 즉시 만료를 허용했다.
 * 그런데 `PaymentDomainService.createPending`은 **PENDING 행을 자기 트랜잭션으로 먼저
 * 커밋**하고, 그 뒤 `initiatePg`가 PG 게이트웨이를 호출하는 동안(δ, PG 응답 대기) 그 행은
 * `PENDING` 그대로다 — READY도 FAILED도 아니다. δ 안에 스위퍼 주기가 겹치면 "방금 시작된
 * 결제 시도"를 신호 없음(None)으로 오판정해 오만료시킬 수 있다 — 이 가드가 존재하는 이유
 * 그 자체인 결과("돈은 받고 예약은 없음")로 이어진다. PENDING을 [OrderPaymentLiveness.Attempting]으로
 * 별도 분류하고, 시도 시작 시각(가장 최근 PENDING 행의 createdAt)을 앵커로 반환한다 —
 * 소비 도메인(booking 등)은 이 앵커와 자신의 주문 생성 시각 중 **최댓값**을 빠른 TTL 기준으로
 * 재평가한다.
 *
 * **2차 무력화 재발 방지 근거**: 이 변경은 PENDING에 *면제*를 주는 게 아니라 *시각 앵커*만
 * 준다 — 예약과 같은 트랜잭션에서 함께 생성된 원 payment는 `attemptSince ≈ order.createdAt`이라
 * 빠른 TTL에 그대로 걸려 정상 만료된다(`CreateBookingUseCase.persistBookingAndPendingPayment`
 * 참고 — booking·원 payment 생성이 한 트랜잭션 안에서 끝난다). **방금 새로 삽입된 재결제
 * 시도(`POST /payments/prepare`, 매 호출 새 idempotencyKey)만** 재-앵커된다. 이미 종결된
 * FAILED/CANCELLED/REFUNDED는 [isAttempting] 대상이 아니므로 영향 없다.
 *
 * 있는 정보(payment 상태·시각)로만 판정한다:
 * - **settled**(COMPLETED): 돈을 받았다 — 절대 만료(취소) 금지. [OrderPaymentLiveness.Settled].
 * - **live**(READY 또는 COMPLETED): 사용자가 checkoutUrl을 받았거나 결제 완료 — 결제가
 *   시작이라도 됐다는 신호. 방치돼도 짧은 TTL로 만료시키면 결제창에 머무는 사용자를
 *   오만료할 위험이 있으므로, 호출 컨텍스트(booking 등)가 소유한 **느린 TTL**을 live payment의
 *   **발급 시각 중 최댓값**에 적용해서만 만료를 허용한다([OrderPaymentLiveness.Live]). 한
 *   주문에 payment가 여러 건인 것은 정상 흐름이므로(장바구니 K1 + 결제 페이지 K2 재개시 등)
 *   최댓값이 아닌 값을 쓰면 "오래된 READY + 방금 READY" 조합에서 판정이 뒤집혀 오만료가 난다.
 * - **attempting**(PENDING): PG 왕복 대기 중 — 아직 결제가 시작됐다는 확답(checkoutUrl)은
 *   없지만 시도가 진행 중이다. **빠른 TTL**이나 앵커를 시도 시작 시각으로 재-앵커한다
 *   ([OrderPaymentLiveness.Attempting], 위 6차 참고).
 * - 그 외(행 없음/FAILED/CANCELLED/REFUNDED): 결제가 시작 못 했거나 종결됐다 — **빠른 TTL**로
 *   만료 허용, 앵커는 호출 컨텍스트가 소유한 주문 생성 시각([OrderPaymentLiveness.None]).
 *   PG prepare 실패로 즉시 FAILED로 전이되는 케이스(F-A 고립 예약)가 여기 포함된다
 *   (`PaymentDomainService.applyPgResult`).
 *
 * TTL 값 자체는 payment가 갖지 않는다 — "슬롯을 얼마나 붙잡아 둘 것인가"는 주문 컨텍스트의
 * 정책이므로 호출 컨텍스트(예: booking)가 소유한다.
 */
object PaymentLivenessClassifier {

    private val LIVE_STATUSES = setOf(PaymentStatus.READY, PaymentStatus.COMPLETED)

    fun isLive(status: PaymentStatus): Boolean = status in LIVE_STATUSES

    fun isSettled(status: PaymentStatus): Boolean = status == PaymentStatus.COMPLETED

    fun isAttempting(status: PaymentStatus): Boolean = status == PaymentStatus.PENDING

    /**
     * orderId별 [OrderPaymentLiveness]를 산출한다 — settled > live > attempting > none
     * 우선순위로 한 주문에 정확히 하나의 판정만 반환한다(같은 주문에 여러 상태의 payment 행이
     * 섞여 있어도 판정은 하나). payment 행이 전혀 없는 orderId는 이 맵에 없다 —
     * [PaymentLivenessQueryResult.of]가 조회 시 [OrderPaymentLiveness.None]을 기본값으로
     * 채운다.
     */
    fun classify(rows: List<PaymentLivenessRow>): PaymentLivenessQueryResult {
        val livenessByOrderId = rows.groupBy { it.orderId }
            .mapValues { (_, group) -> classifyOrder(group) }
        return PaymentLivenessQueryResult(livenessByOrderId)
    }

    private fun classifyOrder(rows: List<PaymentLivenessRow>): OrderPaymentLiveness {
        if (rows.any { isSettled(it.status) }) return OrderPaymentLiveness.Settled

        val liveRows = rows.filter { isLive(it.status) }
        if (liveRows.isNotEmpty()) return OrderPaymentLiveness.Live(liveRows.maxOf { it.createdAt })

        val attemptingRows = rows.filter { isAttempting(it.status) }
        if (attemptingRows.isNotEmpty()) return OrderPaymentLiveness.Attempting(attemptingRows.maxOf { it.createdAt })

        return OrderPaymentLiveness.None
    }
}
