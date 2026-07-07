package com.sportsapp.infrastructure.booking.mysql

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.booking.entity.QSlot.slot
import com.sportsapp.domain.booking.entity.Slot
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext

class SlotJpaRepositoryImpl : SlotQueryDslRepository {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val queryFactory: JPAQueryFactory
        get() = JPAQueryFactory(entityManager)

    override fun findByFacilityId(facilityId: String, programId: Long?): List<Slot> =
        queryFactory.selectFrom(slot)
                    .where(
                        slot.facilityId.eq(facilityId),
                        slot.deletedAt.isNull,
                        programId?.let { slot.programId.eq(it) },
                    )
                    .fetch()
}
