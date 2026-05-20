package com.sportsapp.domain.payment.events

import com.sportsapp.domain.common.AbstractDomainEvent

class PaymentFailedEvent(
    val paymentId: Long,
    val reason: String,
) : AbstractDomainEvent(
    aggregateId = paymentId,
    topic = "payment.failed.v1",
)
