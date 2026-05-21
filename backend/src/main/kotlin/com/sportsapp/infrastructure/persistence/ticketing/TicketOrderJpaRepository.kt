package com.sportsapp.infrastructure.persistence.ticketing

import com.sportsapp.domain.ticketing.TicketOrder
import org.springframework.data.jpa.repository.JpaRepository

interface TicketOrderJpaRepository : JpaRepository<TicketOrder, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): TicketOrder?
    fun findByUserIdAndDeletedAtIsNull(userId: Long): List<TicketOrder>
}
