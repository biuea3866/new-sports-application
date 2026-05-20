package com.sportsapp.domain.payment.events

import com.sportsapp.domain.common.AbstractDomainEvent
import java.math.BigDecimal
import java.time.ZonedDateTime

class PaymentCompletedEvent(
    val paymentId: Long,
    val orderType: String,
    val orderId: Long,
    val amount: BigDecimal,
    val paidAt: ZonedDateTime,
) : AbstractDomainEvent(
    aggregateId = paymentId,
    topic = "payment.completed.v1",
)
