package com.sportsapp.domain.ticketing.event

import com.sportsapp.domain.common.AbstractDomainEvent

class TicketIssuedEvent(
    ticketOrderId: Long,
) : AbstractDomainEvent(
    aggregateId = ticketOrderId,
    topic = "ticket.issued.v1",
)
