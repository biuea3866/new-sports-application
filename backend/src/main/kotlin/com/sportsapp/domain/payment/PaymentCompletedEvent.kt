package com.sportsapp.domain.payment

import com.sportsapp.domain.common.AbstractDomainEvent

class PaymentCompletedEvent(
    paymentId: Long,
) : AbstractDomainEvent(
    aggregateId = paymentId,
    topic = "payment.completed.v1",
)
