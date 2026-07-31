package com.sportsapp.domain.ticketing.repository

import com.sportsapp.domain.ticketing.dto.TicketOrderExpiryCandidate
import com.sportsapp.domain.ticketing.entity.TicketOrder
import java.time.ZonedDateTime

interface TicketOrderRepository {
    fun save(ticketOrder: TicketOrder): TicketOrder
    fun findById(id: Long): TicketOrder?
    fun findByUserId(userId: Long): List<TicketOrder>

    /**
     * W1-11b 만료 스위퍼가 소비 — PENDING 상태·삭제되지 않았으며 createdAt이 before보다 이르고
     * id가 afterId보다 큰 주문을 id 오름차순으로 최대 limit건 조회한다(청크 조회). booking
     * (W1-11c)의 [com.sportsapp.domain.booking.repository.BookingRepository.findPendingCreatedBefore]와
     * 동일한 구조 — createdAt은
     * [com.sportsapp.domain.ticketing.service.TicketingDomainService.filterExpirableTicketOrders]가
     * 느린 TTL·빠른 TTL 재평가 양쪽에서 앵커로 쓴다. afterId 커서로 한 주기 내 이미 훑은(만료
     * 금지 가드로 건너뛴 건 포함) 구간을 다시 스캔하지 않는다.
     */
    fun findPendingCreatedBefore(before: ZonedDateTime, afterId: Long, limit: Int): List<TicketOrderExpiryCandidate>

    /**
     * W1-11b 만료 스위퍼 CAS 쓰기 — 현재 상태가 PENDING일 때만 CANCELLED로 원자적 전이한다
     * (조건부 UPDATE, WHERE status = PENDING). 신규 EXPIRED 상태를 추가하지 않고 기존
     * CANCELLED로 전이한다(만료 여부는 지표·로그로만 구분). MySQL InnoDB는 UPDATE 평가 시
     * 트랜잭션 스냅샷이 아닌 최신 커밋본을 읽으므로(current read), 청크 트랜잭션이 스냅샷을
     * 뜬 이후 다른 트랜잭션이 커밋한 CONFIRMED를 CANCELLED로 덮어쓰는 lost update가 발생하지
     * 않는다. 영향 행 수(affected rows > 0)로 성공 여부를 반환한다.
     */
    fun tryExpire(orderId: Long): Boolean

    /**
     * 결제 확정(webhook) CAS 쓰기 — [tryExpire]와 대칭이다. 현재 상태가 PENDING일 때만
     * CONFIRMED로 원자적 전이한다(조건부 UPDATE, WHERE status = PENDING). 비잠금
     * findById → confirm() → save() 경로는 만료 스위퍼가 먼저 커밋한 CANCELLED를 조건 없는
     * dirty-checking UPDATE로 덮어쓰는 반대 방향 lost update(같은 좌석 이중 발권)를 만들 수
     * 있어 이 CAS로 닫는다. 영향 행 수(affected rows > 0)로 성공 여부를 반환한다.
     */
    fun tryConfirm(orderId: Long, paymentId: Long): Boolean
}
