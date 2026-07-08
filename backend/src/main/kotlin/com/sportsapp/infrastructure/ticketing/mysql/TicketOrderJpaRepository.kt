package com.sportsapp.infrastructure.ticketing.mysql

import com.sportsapp.domain.ticketing.entity.TicketOrder
import org.springframework.data.jpa.repository.JpaRepository

interface TicketOrderJpaRepository : JpaRepository<TicketOrder, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): TicketOrder?
    fun findByUserIdAndDeletedAtIsNull(userId: Long): List<TicketOrder>
}
