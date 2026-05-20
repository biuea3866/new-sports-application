package com.sportsapp.infrastructure.persistence.booking

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.domain.booking.QBooking.booking
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
}
