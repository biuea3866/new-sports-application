package com.sportsapp.infrastructure.persistence.ticketing

import com.sportsapp.domain.ticketing.OrderStatus
import com.sportsapp.domain.ticketing.TicketOrder
import com.sportsapp.domain.ticketing.TicketOrderRepository
import org.springframework.stereotype.Component

@Component
class TicketOrderRepositoryImpl(
    private val ticketOrderJpaRepository: TicketOrderJpaRepository,
) : TicketOrderRepository {

    override fun save(ticketOrder: TicketOrder): TicketOrder =
        ticketOrderJpaRepository.save(ticketOrder)

    override fun findById(id: Long): TicketOrder? =
        ticketOrderJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findByUserId(userId: Long): List<TicketOrder> =
        ticketOrderJpaRepository.findByUserIdAndDeletedAtIsNull(userId)

    override fun countConfirmedSeatsByEventId(eventId: Long): Long =
        ticketOrderJpaRepository.countByLockedEventIdAndStatusAndDeletedAtIsNull(
            eventId,
            OrderStatus.CONFIRMED,
        )
}
