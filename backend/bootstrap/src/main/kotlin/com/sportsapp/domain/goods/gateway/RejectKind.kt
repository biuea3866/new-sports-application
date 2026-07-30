package com.sportsapp.domain.goods.gateway

/**
 * [DropReservationStore.recordReject] 거부 사유 구분 (FR-9 집계용).
 *
 * 구매 거부 사유를 String으로 흘리지 않고 타입으로 고정해 카운터 키 선택을 컴파일 타임에 강제한다.
 */
enum class RejectKind {
    SOLD_OUT,
    TOO_EARLY,
}
