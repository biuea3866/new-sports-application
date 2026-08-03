package com.sportsapp.infrastructure.booking.mysql

import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.booking.dto.BookingExpiryCandidate
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.entity.QBooking.booking
import com.sportsapp.domain.booking.entity.QSlot.slot
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.ZonedDateTime

class BookingJpaRepositoryImpl : BookingQueryDslRepository {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val queryFactory: JPAQueryFactory
        get() = JPAQueryFactory(entityManager)

    override fun findByUserIdAndStatusAndDateRange(
        userId: Long,
        status: BookingStatus?,
        from: ZonedDateTime?,
        to: ZonedDateTime?,
    ): List<Booking> {
        return queryFactory.selectFrom(booking)
                           .where(
                               booking.userId.eq(userId),
                               status?.let { booking.status.eq(it) },
                               from?.let { booking.createdAt.goe(it) },
                               to?.let { booking.createdAt.loe(it) },
                           )
                           .fetch()
    }

    override fun findPageByUserId(
        userId: Long,
        status: BookingStatus?,
        pageable: Pageable,
    ): Page<Booking> {
        val content = queryFactory.selectFrom(booking)
                                  .where(
                                      booking.userId.eq(userId),
                                      status?.let { booking.status.eq(it) },
                                  )
                                  .orderBy(booking.createdAt.desc())
                                  .offset(pageable.offset)
                                  .limit(pageable.pageSize.toLong())
                                  .fetch()

        val total = queryFactory.select(booking.count())
                                .from(booking)
                                .where(
                                    booking.userId.eq(userId),
                                    status?.let { booking.status.eq(it) },
                                )
                                .fetchOne() ?: 0L

        return PageImpl(content, pageable, total)
    }

    override fun findIdsByOwnerUserId(ownerUserId: Long): List<Long> =
        queryFactory.select(booking.id)
                    .from(booking)
                    .join(slot).on(slot.id.eq(booking.slotId))
                    .where(
                        slot.ownerId.eq(ownerUserId),
                        slot.deletedAt.isNull,
                        booking.deletedAt.isNull,
                    )
                    .fetch()

    /**
     * 파트너 스코프 예약 조회 — 예약이 걸린 슬롯의 소유자로 판정한다.
     *
     * 조인 조건에서 `slot.ownerId`를 못 걸면 다른 파트너의 예약이 그대로 새어 나가므로
     * (권한 누수) content/count 두 쿼리 모두 동일한 조건을 적용한다.
     * 삭제된 슬롯·예약은 목록에서 제외한다.
     */
    override fun findPageByOwnerUserId(
        ownerUserId: Long,
        status: BookingStatus?,
        pageable: Pageable,
    ): Page<Booking> {
        val content = queryFactory.selectFrom(booking)
                                  .join(slot).on(slot.id.eq(booking.slotId))
                                  .where(
                                      slot.ownerId.eq(ownerUserId),
                                      slot.deletedAt.isNull,
                                      booking.deletedAt.isNull,
                                      status?.let { booking.status.eq(it) },
                                  )
                                  .orderBy(booking.createdAt.desc())
                                  .offset(pageable.offset)
                                  .limit(pageable.pageSize.toLong())
                                  .fetch()

        val total = queryFactory.select(booking.count())
                                .from(booking)
                                .join(slot).on(slot.id.eq(booking.slotId))
                                .where(
                                    slot.ownerId.eq(ownerUserId),
                                    slot.deletedAt.isNull,
                                    booking.deletedAt.isNull,
                                    status?.let { booking.status.eq(it) },
                                )
                                .fetchOne() ?: 0L

        return PageImpl(content, pageable, total)
    }

    override fun findPendingCreatedBefore(before: ZonedDateTime, afterId: Long, limit: Int): List<BookingExpiryCandidate> {
        return queryFactory.select(Projections.constructor(BookingExpiryCandidate::class.java, booking.id, booking.createdAt))
                           .from(booking)
                           .where(
                               booking.status.eq(BookingStatus.PENDING),
                               booking.createdAt.lt(before),
                               booking.id.gt(afterId),
                               booking.deletedAt.isNull,
                           )
                           .orderBy(booking.id.asc())
                           .limit(limit.toLong())
                           .fetch()
    }

    override fun tryExpire(bookingId: Long): Boolean {
        val affectedRows = queryFactory.update(booking)
                                       .set(booking.status, BookingStatus.EXPIRED)
                                       .set(booking.updatedAt, ZonedDateTime.now())
                                       .where(
                                           booking.id.eq(bookingId),
                                           booking.status.eq(BookingStatus.PENDING),
                                       )
                                       .execute()
        return affectedRows > 0
    }

    override fun tryConfirm(bookingId: Long, paymentId: Long): Boolean {
        val affectedRows = queryFactory.update(booking)
                                       .set(booking.status, BookingStatus.CONFIRMED)
                                       .set(booking.paymentId, paymentId)
                                       .set(booking.updatedAt, ZonedDateTime.now())
                                       .where(
                                           booking.id.eq(bookingId),
                                           booking.status.eq(BookingStatus.PENDING),
                                       )
                                       .execute()
        return affectedRows > 0
    }
}
