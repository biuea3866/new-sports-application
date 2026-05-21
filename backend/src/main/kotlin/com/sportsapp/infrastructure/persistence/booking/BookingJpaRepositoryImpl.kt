package com.sportsapp.infrastructure.persistence.booking

import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.CaseBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.domain.booking.FacilityBookingStats
import com.sportsapp.domain.booking.QBooking.booking
import com.sportsapp.domain.booking.QSlot.slot
import com.sportsapp.domain.payment.QPayment.payment
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

    override fun findNoShowsByOwnerAndPeriod(
        ownerUserId: Long,
        facilityId: String?,
        from: ZonedDateTime,
        to: ZonedDateTime,
        pageable: Pageable,
    ): Page<Booking> {
        val content = queryFactory.selectFrom(booking)
                                  .join(slot).on(slot.id.eq(booking.slotId))
                                  .where(
                                      booking.status.eq(BookingStatus.NO_SHOW),
                                      slot.ownerId.eq(ownerUserId),
                                      facilityId?.let { slot.facilityId.eq(it) },
                                      booking.createdAt.goe(from),
                                      booking.createdAt.loe(to),
                                  )
                                  .orderBy(booking.createdAt.desc())
                                  .offset(pageable.offset)
                                  .limit(pageable.pageSize.toLong())
                                  .fetch()

        val total = queryFactory.select(booking.count())
                                .from(booking)
                                .join(slot).on(slot.id.eq(booking.slotId))
                                .where(
                                    booking.status.eq(BookingStatus.NO_SHOW),
                                    slot.ownerId.eq(ownerUserId),
                                    facilityId?.let { slot.facilityId.eq(it) },
                                    booking.createdAt.goe(from),
                                    booking.createdAt.loe(to),
                                )
                                .fetchOne() ?: 0L

        return PageImpl(content, pageable, total)
    }

    override fun aggregateStatsByFacilityIds(
        facilityIds: List<String>,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<FacilityBookingStats> {
        if (facilityIds.isEmpty()) return emptyList()

        return queryFactory.select(
            Projections.constructor(
                FacilityBookingStats::class.java,
                slot.facilityId,
                booking.id.count(),
                payment.amount.sum().longValue().coalesce(0L),
                CaseBuilder().`when`(booking.status.eq(BookingStatus.NO_SHOW)).then(1L).otherwise(0L).sum().coalesce(0L),
            ),
        )
            .from(booking)
            .join(slot).on(slot.id.eq(booking.slotId))
            .leftJoin(payment).on(payment.id.eq(booking.paymentId))
            .where(
                slot.facilityId.`in`(facilityIds),
                booking.createdAt.goe(from),
                booking.createdAt.loe(to),
                booking.deletedAt.isNull,
            )
            .groupBy(slot.facilityId)
            .fetch()
    }
}
