package com.sportsapp.infrastructure.ticketing.mysql

import com.sportsapp.domain.ticketing.entity.Ticket
import com.sportsapp.domain.ticketing.entity.TicketStatus
import org.springframework.data.jpa.repository.JpaRepository

interface TicketJpaRepository : JpaRepository<Ticket, Long> {
    // Spring Data JPA 파생 쿼리 — 중첩 프로퍼티 경로(ticketOrder.id)를 명시하는 밑줄 표기다.
    // 프레임워크가 메서드명을 직접 파싱해 쿼리를 생성하므로 표준 camelCase 로 바꿀 수 없다
    // (private-be-code-convention "메서드 네이밍" Spring Data 파생 쿼리 예외).
    @Suppress("FunctionNaming")
    fun findByTicketOrder_IdAndDeletedAtIsNull(ticketOrderId: Long): List<Ticket>

    @Suppress("FunctionNaming")
    fun findByTicketOrder_IdAndStatusAndDeletedAtIsNull(ticketOrderId: Long, status: TicketStatus): List<Ticket>
}
