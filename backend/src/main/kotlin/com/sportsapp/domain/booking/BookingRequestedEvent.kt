package com.sportsapp.domain.booking

import com.sportsapp.domain.common.AbstractDomainEvent

class BookingRequestedEvent(
    bookingId: Long,
    val slotId: Long,
    val userId: Long,
) : AbstractDomainEvent(aggregateId = bookingId)
