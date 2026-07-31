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
 * **한계 — idempotencyKey 재사용(p3-3)**: 위 재-앵커는 "매 `POST /payments/prepare` 호출이
 * 새 idempotencyKey를 쓴다"는 전제에서만 성립한다. 클라이언트가 동일 키를 재사용하면
 * `PaymentDomainService.createPending`이 기존 행을 그대로 반환해(새 행 미삽입) 앵커가
 * 갱신되지 않고, `applyPgResult`도 대상 status가 `PENDING`이 아니면 갱신을 건너뛰므로 이미
 * 종결된 행이면 앵커가 영구히 낡은 채로 남는다 — 이때는 checkoutUrl만 유효하게 재반환되고
 * 스위퍼는 앵커 신선도로 이를 구분하지 못한다. 레이스가 아니라 **결정적** 오만료 경로이며,
 * 이 클래스가 관측하는 정보(payment 행의 상태·시각)만으로는 방지할 수 없다 — 클라이언트가
 * idempotencyKey를 매 시도마다 새로 발급하는 것이 전제 조건이다.
 *
 * **7차 재설계 (p1 — 카테고리 우선순위가 최신 시도를 가리는 결함)**: 6차까지는 `live` 행이
 * 한 건이라도 있으면 그 최댓값을 무조건 반환하고 `attempting` 갈래는 아예 평가하지 않았다
 * (`liveRows.isNotEmpty()`로 즉시 return). 그런데 **원 payment가 READY까지 성공한 뒤**(정상
 * 흐름 — F-A처럼 prepare가 실패하는 드문 변종이 아니다) 느린 TTL이 경과하도록 방치되다가,
 * 사용자가 돌아와 재결제(`POST /payments/prepare`)를 시도해 새 PENDING 행이 막 커밋된
 * 시점에 스위퍼가 돌면, 그 새 PENDING이 아무리 최신이어도 오래된 READY의 카테고리
 * 우선순위에 가려 `Live(오래된 시각)`으로 판정돼 즉시 오만료됐다 — "돈은 받고 예약은 없음"이
 * 여기서도 재발한다. 카테고리 우선순위가 아니라 두 카테고리의 최댓값끼리 교차 비교해 실제로
 * 가장 최근에 갱신된 쪽을 앵커로 고르도록 7차에서 수정했었다.
 *
 * **8차 재설계 (p1 — "승자 하나 고르기" 자체가 근본 원인, 방향만 바꿔 재발)**: 7차의 교차
 * 비교도 결국 **하나만** 반환했다. 그런데 소비측(`BookingDomainService.isExpirable`)은
 * live를 **느린 TTL**(예: 60분), attempting을 **빠른 TTL**(예: 15분)로 소비한다 — 서로 다른
 * TTL이다. `newestAttempt > newestLive`이면서 `newestAttempt + 빠른TTL < newestLive + 느린TTL`인
 * 흔한 경우(재결제가 READY 발급 후 느린TTL−빠른TTL 이내, 예: 45분 이내)에는, "더 최신"인
 * attempting을 골라도 그게 **더 짧은 보호 창**(빠른 TTL)으로 이어져 아직 느린 TTL 안에 있는
 * READY까지 통째로 버려진다 — 방향만 바뀐 동일 결함("돈은 받고 예약은 없음")이었다. 게다가
 * 재결제를 시도할수록(attempting 증거가 늘수록) 보호가 짧아지는 비단조 결함이었다.
 * "승자 하나"로는 **증거가 추가되면 보호가 절대 줄지 않는다(단조성)**는 불변식을 표현할 수
 * 없다 — 그래서 live 행이 하나라도 있으면 승자를 고르지 않고 [OrderPaymentLiveness.Live]가
 * `since`(느린 TTL 앵커)와 `attemptSince`(빠른 TTL 앵커, 있으면)를 **함께** 반환한다. 소비측이
 * 두 창을 모두 검사해 단조성을 보장한다(위 [OrderPaymentLiveness] KDoc "8차 재설계" 참고).
 *
 * 있는 정보(payment 상태·시각)로만 판정한다:
 * - **settled**(COMPLETED): 돈을 받았다 — 절대 만료(취소) 금지. [OrderPaymentLiveness.Settled].
 * - **live**(READY 또는 COMPLETED): 사용자가 checkoutUrl을 받았거나 결제 완료 — 결제가
 *   시작이라도 됐다는 신호. 방치돼도 짧은 TTL로 만료시키면 결제창에 머무는 사용자를
 *   오만료할 위험이 있으므로, 호출 컨텍스트(booking 등)가 소유한 **느린 TTL**을 live payment의
 *   **행 생성 시각 중 최댓값**에 적용해서만 만료를 허용한다([OrderPaymentLiveness.Live.since]). 한
 *   주문에 payment가 여러 건인 것은 정상 흐름이므로(장바구니 K1 + 결제 페이지 K2 재개시 등)
 *   최댓값이 아닌 값을 쓰면 "오래된 READY + 방금 READY" 조합에서 판정이 뒤집혀 오만료가 난다.
 * - **attempting**(PENDING): PG 왕복 대기 중 — 아직 결제가 시작됐다는 확답(checkoutUrl)은
 *   없지만 시도가 진행 중이다. live 행이 하나도 없으면 [OrderPaymentLiveness.Attempting]으로
 *   단독 반환하고, **빠른 TTL**이나 앵커를 시도 시작 시각으로 재-앵커한다(위 6차 참고).
 * - **live와 attempting이 함께 있을 때(8차)**: live 최댓값을 `since`, attempting 최댓값을
 *   `attemptSince`로 **함께 담아** [OrderPaymentLiveness.Live]로 반환한다(승자를 고르지 않는다).
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
     * orderId별 [OrderPaymentLiveness]를 산출한다 — settled가 있으면 그것 하나, live 행이
     * 하나라도 있으면 `since`(live 최댓값)·`attemptSince`(attempting 최댓값, 없으면 `null`)를
     * **함께 담은** [OrderPaymentLiveness.Live] 하나, live가 전혀 없고 attempting만 있으면
     * [OrderPaymentLiveness.Attempting] 하나(같은 주문에 여러 상태의 payment 행이 섞여
     * 있어도 판정은 하나 — 승자를 고르는 게 아니라 카테고리별 최댓값을 모두 전달한다. 8차 —
     * 위 클래스 KDoc "8차 재설계" 참고). payment 행이 전혀 없는 orderId는 이 맵에 없다 —
     * [PaymentLivenessQueryResult.of]가 조회 시 [OrderPaymentLiveness.None]을 기본값으로
     * 채운다.
     */
    fun classify(rows: List<PaymentLivenessRow>): PaymentLivenessQueryResult {
        val livenessByOrderId = rows.groupBy { it.orderId }
            .mapValues { (_, group) -> classifyOrder(group) }
        return PaymentLivenessQueryResult(livenessByOrderId)
    }

    /**
     * **8차 재설계 (p1)**: 7차는 `newestLive`·`newestAttempt`를 교차 비교해 **하나만** 골라
     * 반환했다 — 그런데 소비측이 두 앵커를 서로 다른 TTL로 소비하는 이상 "하나만 고르기"는
     * 방향만 바꿔 같은 결함을 재발시켰다(클래스 KDoc "8차 재설계" 참고). 이제 live 행이
     * 하나라도 있으면 승자를 고르지 않고 `newestLive`와 `newestAttempt`를 **모두**
     * [OrderPaymentLiveness.Live]에 담아 반환한다 — 소비측이 두 창(느린 TTL by `since`, 빠른
     * TTL by `attemptSince`)을 모두 검사해 단조성을 스스로 보장하게 한다.
     */
    private fun classifyOrder(rows: List<PaymentLivenessRow>): OrderPaymentLiveness {
        if (rows.any { isSettled(it.status) }) return OrderPaymentLiveness.Settled

        val newestLive = rows.filter { isLive(it.status) }.maxOfOrNull { it.createdAt }
        val newestAttempt = rows.filter { isAttempting(it.status) }.maxOfOrNull { it.createdAt }

        return when {
            newestLive != null -> OrderPaymentLiveness.Live(since = newestLive, attemptSince = newestAttempt)
            newestAttempt != null -> OrderPaymentLiveness.Attempting(newestAttempt)
            else -> OrderPaymentLiveness.None
        }
    }
}
