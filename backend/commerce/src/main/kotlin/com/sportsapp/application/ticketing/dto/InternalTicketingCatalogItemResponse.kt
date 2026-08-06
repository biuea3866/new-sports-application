package com.sportsapp.application.ticketing.dto

import com.sportsapp.domain.ticketing.dto.EventWithMinSeatPrice
import com.sportsapp.domain.ticketing.entity.EventStatus
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * edge catalog 통합검색(BE-07)이 `CatalogSearchGateway.searchTicketingEvents` 원격 구현(2단계)으로
 * 소비할 계약 응답 (S2-03).
 *
 * [price] 는 **최저 좌석가**다 — 경기는 좌석마다 가격이 달라 대표가로 노출하고, 좌석 미등록 경기는
 * null 이다(0 으로 방어하지 않는다). [locationName]·[scheduledAt] 은 같은 제목의 경기를 사용자가
 * 구분하는 **실데이터**라 공급자가 채운다.
 *
 * `itemType`(TICKET 고정)·`sellerType`(null 고정)·`detailPath` 는 상수 판정이라 edge 파사드가
 * 만든다 — 이 응답에 두지 않는다.
 */
data class InternalTicketingCatalogItemResponse(
    val sourceId: Long,
    val title: String,
    val price: BigDecimal?,
    val status: EventStatus,
    val createdAt: ZonedDateTime,
    val locationName: String,
    val scheduledAt: ZonedDateTime,
) {
    companion object {
        fun of(eventWithMinSeatPrice: EventWithMinSeatPrice): InternalTicketingCatalogItemResponse =
            InternalTicketingCatalogItemResponse(
                sourceId = eventWithMinSeatPrice.event.id,
                title = eventWithMinSeatPrice.event.title,
                price = eventWithMinSeatPrice.minSeatPrice,
                status = eventWithMinSeatPrice.event.status,
                createdAt = eventWithMinSeatPrice.event.createdAt,
                locationName = eventWithMinSeatPrice.event.venue,
                scheduledAt = eventWithMinSeatPrice.event.startsAt,
            )
    }
}
