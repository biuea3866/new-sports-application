package com.sportsapp.domain.ticketing.dto

import com.sportsapp.domain.ticketing.entity.Event

/**
 * 경기 + 좌석 판매 집계 — 주최자 경기 목록이 카드마다 `판매 {soldSeats} / {totalSeats}석`을 렌더한다.
 *
 * 목록은 한 페이지에 여러 경기를 싣기 때문에 경기당 좌석을 개별 조회하면 N+1이 된다.
 * 페이지 전체의 좌석 수·판매 수를 각각 한 번의 집계 조회로 채운 뒤 이 타입으로 묶는다
 * (카탈로그 대표가를 채우는 [EventWithMinSeatPrice]와 동일한 구조).
 *
 * 잔여 좌석은 호출부가 빼서 구하지 않고 [availableSeats]로 노출한다 — 뺄셈 위치가 흩어지면
 * 화면마다 다른 잔여 수가 나올 수 있다.
 */
data class EventWithSeatCounts(
    val event: Event,
    val totalSeats: Long,
    val soldSeats: Long,
) {
    /** 잔여 좌석. 판매 수가 총 좌석을 넘는 비정상 데이터에서도 음수로 내려가지 않게 0에서 막는다. */
    val availableSeats: Long get() = (totalSeats - soldSeats).coerceAtLeast(0L)
}
