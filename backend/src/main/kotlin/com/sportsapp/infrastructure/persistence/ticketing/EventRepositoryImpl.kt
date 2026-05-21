package com.sportsapp.infrastructure.persistence.ticketing

import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventRepository
import com.sportsapp.domain.ticketing.EventStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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

    override fun findByOwnerId(ownerId: Long, pageable: Pageable): Page<Event> =
        eventJpaRepository.findByOwnerIdAndDeletedAtIsNull(ownerId, pageable)

    override fun findByOwnerId(ownerId: Long, status: EventStatus?, pageable: Pageable): Page<Event> =
        if (status != null) {
            eventJpaRepository.findByOwnerIdAndStatusAndDeletedAtIsNull(ownerId, status, pageable)
        } else {
            eventJpaRepository.findByOwnerIdAndDeletedAtIsNull(ownerId, pageable)
        }

    override fun findByIdAndOwnerId(id: Long, ownerId: Long): Event? =
        eventJpaRepository.findByIdAndOwnerIdAndDeletedAtIsNull(id, ownerId)

    override fun countByOwnerIdGroupByStatus(ownerId: Long): Map<EventStatus, Long> =
        eventJpaRepository.countByOwnerIdGroupByStatus(ownerId)
}
