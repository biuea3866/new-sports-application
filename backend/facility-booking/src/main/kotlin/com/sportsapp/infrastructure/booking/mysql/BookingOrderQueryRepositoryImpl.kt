package com.sportsapp.infrastructure.booking.mysql

import com.querydsl.core.Tuple
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.booking.dto.BookingOrderItem
import com.sportsapp.domain.booking.entity.QBooking.booking
import com.sportsapp.domain.booking.entity.QSlot.slot
import com.sportsapp.domain.booking.repository.BookingOrderQueryRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

/**
 * bookings → slots(둘 다 booking 컨텍스트) 조인으로 사용자별 주문 + title 라벨을 구성한다.
 * Slot이 소프트 삭제되었거나 참조가 부재(고아 slotId)인 경우 leftJoin이 null을 반환해
 * [BookingOrderItem.of]가 기본 라벨로 방어한다.
 */
@Repository
class BookingOrderQueryRepositoryImpl : BookingOrderQueryRepository {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val queryFactory: JPAQueryFactory
        get() = JPAQueryFactory(entityManager)

    override fun findByUserId(userId: Long): List<BookingOrderItem> =
        queryFactory
            .select(
                booking.id,
                booking.slotId,
                booking.userId,
                booking.status,
                booking.paymentId,
                booking.createdAt,
                booking.amount,
                slot.date,
                slot.timeRange,
            )
            .from(booking)
            .leftJoin(slot).on(slot.id.eq(booking.slotId).and(slot.deletedAt.isNull))
            .where(booking.userId.eq(userId))
            .orderBy(booking.createdAt.desc())
            .fetch()
            .map(::toBookingOrderItem)

    private fun toBookingOrderItem(tuple: Tuple): BookingOrderItem = BookingOrderItem.of(
        bookingId = requireNotNull(tuple.get(booking.id)),
        slotId = requireNotNull(tuple.get(booking.slotId)),
        userId = requireNotNull(tuple.get(booking.userId)),
        status = requireNotNull(tuple.get(booking.status)),
        createdAt = requireNotNull(tuple.get(booking.createdAt)),
        slotLabelSource = BookingOrderItem.SlotLabelSource(
            date = tuple.get(slot.date),
            timeRange = tuple.get(slot.timeRange),
        ),
        chargeSource = BookingOrderItem.ChargeSource(
            paymentId = tuple.get(booking.paymentId),
            amount = tuple.get(booking.amount),
        ),
    )
}
