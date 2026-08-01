package com.sportsapp.application.ticketing.dto

import com.sportsapp.domain.ticketing.dto.EventWithSeatCounts
import java.time.ZonedDateTime

/**
 * 주최자 경기 목록 응답 — 포털 "내 경기 목록"이 카드마다 `판매 {soldSeats} / {totalSeats}석`을 렌더한다.
 *
 * 공개 카탈로그 목록([EventResponse])과 필드 구성이 다르므로 확장하지 않고 별도로 정의한다
 * (상세 응답 [MyEventWithSalesResponse]와 같은 이유). 카탈로그는 좌석 집계가 필요 없어
 * [EventResponse]에 좌석 필드를 얹으면 공개 목록까지 불필요한 집계 조회를 지게 된다.
 */
data class MyEventResponse(
    val id: Long,
    val title: String,
    val venue: String,
    val startsAt: ZonedDateTime,
    val status: String,
    val totalSeats: Long,
    val soldSeats: Long,
    val availableSeats: Long,
) {
    companion object {
        fun of(eventWithSeatCounts: EventWithSeatCounts): MyEventResponse = MyEventResponse(
            id = eventWithSeatCounts.event.id,
            title = eventWithSeatCounts.event.title,
            venue = eventWithSeatCounts.event.venue,
            startsAt = eventWithSeatCounts.event.startsAt,
            status = eventWithSeatCounts.event.status.name,
            totalSeats = eventWithSeatCounts.totalSeats,
            soldSeats = eventWithSeatCounts.soldSeats,
            availableSeats = eventWithSeatCounts.availableSeats,
        )
    }
}
