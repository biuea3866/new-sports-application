package com.sportsapp.domain.booking.event

import com.sportsapp.domain.common.AbstractDomainEvent
import java.math.BigDecimal

class BookingRefundRequestedEvent(
    bookingId: Long,
    val paymentId: Long,
    val refundAmount: BigDecimal,
    val reason: String,
) : AbstractDomainEvent(aggregateId = bookingId)
