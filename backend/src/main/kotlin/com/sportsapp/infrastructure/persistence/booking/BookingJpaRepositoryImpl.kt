package com.sportsapp.infrastructure.persistence.booking

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.domain.booking.QBooking.booking
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
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
}
