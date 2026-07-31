package com.sportsapp.application.ticketing.dto

import com.sportsapp.domain.ticketing.dto.EventSalesInfo
import java.time.ZonedDateTime

/**
 * 주최자 경기 상세 응답 — 판매 집계와 **좌석별 판매 여부**를 함께 싣는다.
 *
 * 포털 경기 상세 화면이 좌석 표를 렌더하므로 [seats]가 계약의 일부다(누락 시 화면 백지).
 */
data class MyEventWithSalesResponse(
    val id: Long,
    val title: String,
    val venue: String,
    val startsAt: ZonedDateTime,
    val status: String,
    val totalSeats: Int,
    val totalSold: Long,
    val totalAvailable: Long,
    val seats: List<EventSeatSalesResponse>,
) {
    companion object {
        fun of(salesInfo: EventSalesInfo): MyEventWithSalesResponse = MyEventWithSalesResponse(
            id = salesInfo.event.id,
            title = salesInfo.event.title,
            venue = salesInfo.event.venue,
            startsAt = salesInfo.event.startsAt,
            status = salesInfo.event.status.name,
            totalSeats = salesInfo.seats.size,
            totalSold = salesInfo.soldCount,
            totalAvailable = salesInfo.availableCount,
            seats = salesInfo.seats.map { seat ->
                EventSeatSalesResponse(
                    id = seat.id,
                    label = seat.displayLabel,
                    sold = salesInfo.isSold(seat.id),
                )
            },
        )
    }
}

/** 좌석 한 석의 판매 표시 정보. */
data class EventSeatSalesResponse(
    val id: Long,
    val label: String,
    val sold: Boolean,
)
