package com.sportsapp.infrastructure.ticketing.mysql

import com.sportsapp.domain.ticketing.dto.TicketOrderExpiryCandidate
import com.sportsapp.domain.ticketing.entity.TicketOrder
import com.sportsapp.domain.ticketing.repository.TicketOrderRepository
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

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

    override fun findPendingCreatedBefore(before: ZonedDateTime, afterId: Long, limit: Int): List<TicketOrderExpiryCandidate> =
        ticketOrderJpaRepository.findPendingCreatedBefore(before, afterId, limit)

    override fun tryExpire(orderId: Long): Boolean =
        ticketOrderJpaRepository.tryExpire(orderId)

    override fun tryConfirm(orderId: Long, paymentId: Long): Boolean =
        // named argument 강제 — 인접한 동일 타입(Long) 위치 인자 뒤바뀜 방지.
        ticketOrderJpaRepository.tryConfirm(orderId = orderId, paymentId = paymentId)
}
