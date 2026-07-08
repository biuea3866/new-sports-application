package com.sportsapp.infrastructure.booking.mysql

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.entity.QBooking.booking
import com.sportsapp.domain.booking.entity.QSlot.slot
import com.sportsapp.domain.booking.repository.BookingKpiQueryRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime

@Repository
class BookingKpiQueryRepositoryImpl : BookingKpiQueryRepository {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val queryFactory: JPAQueryFactory
        get() = JPAQueryFactory(entityManager)

    override fun countConfirmedByOwnerUserIdAndDateRange(
        ownerUserId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): Long =
        queryFactory.select(booking.count())
                    .from(booking)
                    .join(slot).on(slot.id.eq(booking.slotId))
                    .where(
                        slot.ownerId.eq(ownerUserId),
                        slot.deletedAt.isNull,
                        booking.status.eq(BookingStatus.CONFIRMED),
                        booking.createdAt.goe(from),
                        booking.createdAt.loe(to),
                    )
                    .fetchOne() ?: 0L

    override fun countRefundedByOwnerUserIdAndDateRange(
        ownerUserId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): Long =
        queryFactory.select(booking.count())
                    .from(booking)
                    .join(slot).on(slot.id.eq(booking.slotId))
                    .where(
                        slot.ownerId.eq(ownerUserId),
                        slot.deletedAt.isNull,
                        booking.status.eq(BookingStatus.REFUNDED),
                        booking.createdAt.goe(from),
                        booking.createdAt.loe(to),
                    )
                    .fetchOne() ?: 0L

    override fun sumSlotCapacityByOwnerUserIdAndDateRange(
        ownerUserId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): Long {
        val result = queryFactory.select(slot.capacity.sum())
                                 .from(slot)
                                 .where(
                                     slot.ownerId.eq(ownerUserId),
                                     slot.deletedAt.isNull,
                                     slot.date.goe(from),
                                     slot.date.loe(to),
                                 )
                                 .fetchOne()
        return result?.toLong() ?: 0L
    }

    override fun findTopFacilityIdsByOwnerUserIdAndDateRange(
        ownerUserId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
        limit: Int,
    ): List<String> =
        queryFactory.select(slot.facilityId)
                    .from(booking)
                    .join(slot).on(slot.id.eq(booking.slotId))
                    .where(
                        slot.ownerId.eq(ownerUserId),
                        slot.deletedAt.isNull,
                        booking.status.eq(BookingStatus.CONFIRMED),
                        booking.createdAt.goe(from),
                        booking.createdAt.loe(to),
                    )
                    .groupBy(slot.facilityId)
                    .orderBy(booking.id.count().desc())
                    .limit(limit.toLong())
                    .fetch()
}
