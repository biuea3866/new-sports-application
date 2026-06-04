package com.sportsapp.application.ticketing.dto

import com.sportsapp.domain.ticketing.dto.EventSalesInfo
import java.time.ZonedDateTime

data class MyEventWithSalesResponse(
    val id: Long,
    val title: String,
    val venue: String,
    val startsAt: ZonedDateTime,
    val status: String,
    val totalSeats: Int,
    val totalSold: Long,
    val totalAvailable: Long,
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
        )
    }
}
