package com.sportsapp.domain.ticketing.dto

import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.Seat

/**
 * 경기 판매 현황 — 좌석 목록과 발권 집계를 함께 담는다.
 *
 * [soldSeatIds]는 발권 완료(ISSUED) 티켓이 점유한 좌석 id 집합이다. 주최자 화면이 좌석별
 * 판매 여부를 표시하려면 집계 수치만으로는 부족해 좌석 단위 판정이 필요하다.
 */
data class EventSalesInfo(
    val event: Event,
    val seats: List<Seat>,
    val soldCount: Long,
    val soldSeatIds: Set<Long>,
) {
    val availableCount: Long get() = seats.size - soldCount

    fun isSold(seatId: Long): Boolean = seatId in soldSeatIds
}
