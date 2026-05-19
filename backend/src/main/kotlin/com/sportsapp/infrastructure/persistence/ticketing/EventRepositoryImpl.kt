package com.sportsapp.infrastructure.persistence.ticketing

import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class EventRepositoryImpl(
    private val eventJpaRepository: EventJpaRepository,
) : EventRepository {

    override fun save(event: Event): Event =
        eventJpaRepository.save(event)

    override fun findById(id: Long): Event? =
        eventJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun softDelete(id: Long, deletedBy: Long?) {
        val event = eventJpaRepository.findByIdOrNull(id) ?: return
        event.softDelete(deletedBy)
        eventJpaRepository.save(event)
    }
}
