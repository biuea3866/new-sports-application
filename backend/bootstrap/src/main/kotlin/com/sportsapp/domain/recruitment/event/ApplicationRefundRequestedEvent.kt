package com.sportsapp.domain.recruitment.event

import com.sportsapp.domain.common.AbstractDomainEvent
import java.math.BigDecimal

class ApplicationRefundRequestedEvent(
    applicationId: Long,
    val paymentId: Long?,
    val refundAmount: BigDecimal,
    val reason: String,
) : AbstractDomainEvent(aggregateId = applicationId)
