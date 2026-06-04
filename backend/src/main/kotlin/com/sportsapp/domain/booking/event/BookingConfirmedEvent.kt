package com.sportsapp.domain.booking.event

import com.sportsapp.domain.common.AbstractDomainEvent

class BookingConfirmedEvent(
    bookingId: Long,
    val paymentId: Long,
) : AbstractDomainEvent(
    aggregateId = bookingId,
    topic = "booking.confirmed.v1",
)
