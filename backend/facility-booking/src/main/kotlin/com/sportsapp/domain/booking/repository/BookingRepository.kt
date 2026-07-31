package com.sportsapp.domain.booking.repository

import com.sportsapp.domain.booking.dto.BookingExpiryCandidate
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.ZonedDateTime

interface BookingRepository {
    fun save(booking: Booking): Booking
    fun findById(id: Long): Booking?
    fun findByUserIdAndStatus(userId: Long, status: BookingStatus): List<Booking>
    fun findByUserIdAndStatusAndDateRange(
        userId: Long,
        status: BookingStatus?,
        from: ZonedDateTime?,
        to: ZonedDateTime?,
    ): List<Booking>
    fun findPageByUserId(
        userId: Long,
        status: BookingStatus?,
        pageable: Pageable,
    ): Page<Booking>
    fun countBySlotIdAndStatusIn(slotId: Long, statuses: List<BookingStatus>): Long

    /**
     * W1-11c 만료 스위퍼가 소비 — PENDING 상태·삭제되지 않았으며 createdAt이 before보다 이르고
     * id가 afterId보다 큰 예약을 id 오름차순으로 최대 limit건 조회한다(청크 조회). id뿐 아니라
     * createdAt도 함께 반환한다 — 4차 재설계에서 payment의 live(READY 이상) 판정과 결합해
     * "느린 TTL(readyTtlMinutes)을 지났는가"를 판단하려면 후보의 createdAt이 필요하다
     * ([com.sportsapp.domain.booking.service.BookingDomainService.filterExpirable] 참고).
     * afterId 커서로 한 주기 내 이미 훑은(만료 금지 가드로 건너뛴 건 포함) 구간을 다시 스캔하지
     * 않는다 — 커서 없이는 결제 진행 중이라 건너뛴 예약이 다음 청크에서 계속 재조회되어
     * 스위퍼가 진행하지 못하는 head-of-line blocking이 생긴다.
     */
    fun findPendingCreatedBefore(before: ZonedDateTime, afterId: Long, limit: Int): List<BookingExpiryCandidate>

    /**
     * W1-11c 만료 스위퍼 CAS 쓰기 — 현재 상태가 PENDING일 때만 EXPIRED로 원자적 전이한다
     * (조건부 UPDATE, WHERE status = PENDING). MySQL InnoDB는 UPDATE 평가 시 트랜잭션
     * 스냅샷이 아닌 최신 커밋본을 읽으므로(current read), 청크 트랜잭션이 REPEATABLE READ
     * 스냅샷을 뜬 이후 다른 트랜잭션이 커밋한 CONFIRMED를 EXPIRED로 덮어쓰는 lost update가
     * 발생하지 않는다. 영향 행 수(affected rows > 0)로 성공 여부를 반환한다.
     */
    fun tryExpire(bookingId: Long): Boolean

    /**
     * 결제 확정(webhook) CAS 쓰기 — [tryExpire]와 대칭이다. 현재 상태가 PENDING일 때만
     * CONFIRMED로 원자적 전이한다(조건부 UPDATE, WHERE status = PENDING). 비잠금
     * findById → confirm() → save() 경로는 만료 스위퍼가 먼저 커밋한 EXPIRED를 조건 없는
     * dirty-checking UPDATE로 덮어쓰는 반대 방향 lost update(오버부킹)를 만들 수 있어 이
     * CAS로 닫는다. 영향 행 수(affected rows > 0)로 성공 여부를 반환한다.
     */
    fun tryConfirm(bookingId: Long, paymentId: Long): Boolean
    fun countConfirmedByOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): Long
    fun countRefundedByOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): Long
    fun sumSlotCapacityByOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): Long
    fun findTopFacilityIdsByOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime, limit: Int): List<String>
}
