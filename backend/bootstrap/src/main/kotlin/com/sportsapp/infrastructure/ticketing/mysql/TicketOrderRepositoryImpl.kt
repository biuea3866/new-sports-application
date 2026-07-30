package com.sportsapp.infrastructure.ticketing.mysql

import com.sportsapp.domain.ticketing.entity.TicketOrder
import com.sportsapp.domain.ticketing.repository.TicketOrderRepository
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
}
