package com.sportsapp.domain.booking.event

import com.sportsapp.domain.common.AbstractDomainEvent

class BookingRequestedEvent(
    bookingId: Long,
    val slotId: Long,
    val userId: Long,
) : AbstractDomainEvent(aggregateId = bookingId)
