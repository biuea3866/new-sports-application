package com.sportsapp.domain.ticketing.repository

import java.math.BigDecimal

interface SeatCustomRepository {
    /** 발권 완료(ISSUED) 티켓이 점유한 좌석 id — 좌석별 판매 여부 판정용. */
    fun findSoldSeatIdsByEventId(eventId: Long): Set<Long>

    /** 경기별 최저 좌석가 — 카탈로그 대표가용. 좌석이 없는 경기는 결과에서 빠진다. */
    fun findMinPriceByEventIds(eventIds: List<Long>): Map<Long, BigDecimal>

    /**
     * 경기별 총 좌석 수 — 주최자 경기 목록의 `{totalSeats}석` 표시용.
     * 좌석이 없는 경기는 결과에서 빠지므로 호출부가 0으로 보정한다.
     */
    fun countSeatsByEventIds(eventIds: List<Long>): Map<Long, Long>

    /**
     * 경기별 판매 좌석 수(발권 완료 기준) — 주최자 경기 목록의 `판매 {soldSeats}` 표시용.
     * 판매가 없는 경기는 결과에서 빠지므로 호출부가 0으로 보정한다.
     */
    fun countSoldSeatsByEventIds(eventIds: List<Long>): Map<Long, Long>
    fun sumTotalSeatsByOwnerId(ownerId: Long): Long
    fun sumSoldSeatsByOwnerId(ownerId: Long): Long
}
