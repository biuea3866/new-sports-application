package com.sportsapp.domain.payment.service

import com.sportsapp.domain.payment.entity.PaymentStatus
import java.time.ZonedDateTime

/**
 * 만료 스위퍼(W1-11a~d 공통) 만료 금지 가드 판정 — 순수 함수.
 *
 * PaymentStatus 전이는 PENDING -> READY -> COMPLETED(-> REFUNDED) 이며, 결제 개시
 * (initiatePg) 시점에 이미 PENDING 행이 생성된다. 즉 주문마다 PENDING 또는 READY payment
 * 행이 항상 존재하므로, status만으로 "만료 금지"를 판정하면(구 findCompletedOrderIds ->
 * findUnexpirableOrderIds 1차 확장) 모든 PENDING 주문이 전건 만료 금지로 걸려 스위퍼가
 * 완전히 무력화된다.
 *
 * PENDING/READY는 두 가지 상반된 의미를 가진다:
 * 1. 주문 생성 시 만들어진 뒤 방치된 결제 -> 만료를 허용해야 한다
 * 2. 사용자가 지금 PG 결제창에 있는 결제 -> 만료 금지해야 한다
 *
 * 상태만으로는 이 둘을 구분할 수 없고, `updatedAt`(가장 최근 상태 변경 시각)이 갈라야 한다.
 * `activeSince`(활동 창 시작 시각) 이후에 갱신됐으면 "지금 진행 중"으로, 그 이전이면
 * "방치됨"으로 본다.
 *
 * 판정 규칙:
 * - COMPLETED: 돈을 받았다 -> 조용히 만료(취소) 금지
 * - PENDING/READY + updatedAt >= activeSince: 사용자가 결제 진행 중 -> 만료 금지
 * - PENDING/READY + updatedAt < activeSince: 방치된 결제 -> 만료 허용
 * - CANCELLED/FAILED: 종료된 실패 -> 만료 허용
 * - REFUNDED: 환불 완료. 돈이 이미 돌아갔으므로 PENDING 주문은 정리(만료)돼야 한다 -> 만료 허용
 */
object PaymentExpiryGuard {

    private val ACTIVELY_PROGRESSING_STATUSES = setOf(PaymentStatus.PENDING, PaymentStatus.READY)

    fun isUnexpirable(status: PaymentStatus, updatedAt: ZonedDateTime, activeSince: ZonedDateTime): Boolean =
        status == PaymentStatus.COMPLETED || isActivelyProgressing(status, updatedAt, activeSince)

    private fun isActivelyProgressing(
        status: PaymentStatus,
        updatedAt: ZonedDateTime,
        activeSince: ZonedDateTime,
    ): Boolean = status in ACTIVELY_PROGRESSING_STATUSES && !updatedAt.isBefore(activeSince)
}
