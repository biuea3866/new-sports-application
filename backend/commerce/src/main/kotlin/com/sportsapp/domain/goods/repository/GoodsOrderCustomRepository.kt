package com.sportsapp.domain.goods.repository

import com.sportsapp.domain.goods.dto.GoodsOrderExpiryCandidate
import com.sportsapp.domain.goods.dto.GoodsOrderWithTitle
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface GoodsOrderCustomRepository {
    fun countConfirmedByProductOwnerUserId(ownerUserId: Long): Long
    fun sumRevenueByProductOwnerUserId(ownerUserId: Long): BigDecimal
    fun sumRevenueByProductOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): BigDecimal

    /** order 통합조회(BE-08 예정)용 — 대표 상품명(title) 조인 읽기(BE-03). */
    fun findBy(userId: Long, pageable: Pageable): Page<GoodsOrderWithTitle>

    /** 주문 상세(단건)용 — [findBy]와 동일한 buildTitle 로직으로 대표 상품명을 조회한다. */
    fun findTitleFor(orderId: Long): String

    /**
     * W1-11a 만료 스위퍼가 소비 — PENDING 상태·삭제되지 않았으며 createdAt이 before보다
     * 이르고 id가 afterId보다 큰 주문을 id 오름차순으로 최대 limit건 조회한다(청크 조회).
     * `facility-booking`(W1-11c) `BookingRepository.findPendingCreatedBefore`와 동일한
     * 이유로 createdAt도 함께 반환한다 —
     * [com.sportsapp.domain.goods.service.GoodsDomainService.filterExpirable]가
     * [com.sportsapp.domain.common.payment.OrderPaymentLiveness.allowsExpiry]의
     * `orderCreatedAt` 인자로 사용한다. afterId 커서로 한 주기 내 이미 훑은(만료 금지
     * 가드로 건너뛴 건 포함) 구간을 다시 스캔하지 않는다 — 커서 없이는 결제 진행 중이라
     * 건너뛴 주문이 다음 청크에서 계속 재조회되어 스위퍼가 진행하지 못하는
     * head-of-line blocking이 생긴다.
     */
    fun findPendingCreatedBefore(before: ZonedDateTime, afterId: Long, limit: Int): List<GoodsOrderExpiryCandidate>

    /**
     * W1-11a 만료 스위퍼 CAS 쓰기 — 현재 상태가 PENDING일 때만 CANCELLED로 원자적 전이한다
     * (조건부 UPDATE, WHERE status = PENDING). MySQL InnoDB는 UPDATE 평가 시 트랜잭션
     * 스냅샷이 아닌 최신 커밋본을 읽으므로(current read), 청크 트랜잭션이 REPEATABLE READ
     * 스냅샷을 뜬 이후 다른 트랜잭션이 커밋한 CONFIRMED를 CANCELLED로 덮어쓰는 lost
     * update가 발생하지 않는다. 신규 상태(EXPIRED)를 추가하지 않고 기존 CANCELLED를
     * 재사용한다(티켓 결정). 영향 행 수(affected rows > 0)로 성공 여부를 반환한다.
     * 재고 복원·아이템 soft-delete는 이 메서드가 아니라
     * [com.sportsapp.domain.goods.service.GoodsDomainService.expireOrders]가 CAS 성공
     * 이후 별도로 수행한다(no-business-flow-in-infra — 이 메서드는 주문 상태 전이만 한다).
     */
    fun tryExpire(orderId: Long): Boolean

    /**
     * 결제 확정(webhook) CAS 쓰기 — [tryExpire]와 대칭이다. 현재 상태가 PENDING일 때만
     * CONFIRMED로 원자적 전이한다(조건부 UPDATE, WHERE status = PENDING). 비잠금
     * findById → markPaid() → save() 경로는 만료 스위퍼가 먼저 커밋한 CANCELLED를 조건
     * 없는 dirty-checking UPDATE로 덮어쓰는 반대 방향 lost update를 만들 수 있어 이 CAS로
     * 닫는다. 영향 행 수(affected rows > 0)로 성공 여부를 반환한다.
     */
    fun tryConfirm(orderId: Long, paymentId: Long): Boolean
}
