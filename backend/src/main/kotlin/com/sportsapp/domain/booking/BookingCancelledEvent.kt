package com.sportsapp.domain.booking

import com.sportsapp.domain.common.AbstractDomainEvent

class BookingCancelledEvent(
    bookingId: Long,
    val cancelledByUserId: Long,
    val reason: String?,
) : AbstractDomainEvent(aggregateId = bookingId)
