package com.sportsapp.infrastructure.persistence.booking

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.infrastructure.persistence.booking.QBookingJpaEntity.bookingJpaEntity
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
    ): List<BookingJpaEntity> {
        return queryFactory.selectFrom(bookingJpaEntity)
                           .where(
                               bookingJpaEntity.userId.eq(userId),
                               status?.let { bookingJpaEntity.status.eq(it) },
                               from?.let { bookingJpaEntity.createdAt.goe(it) },
                               to?.let { bookingJpaEntity.createdAt.loe(it) },
                           )
                           .fetch()
    }
}
