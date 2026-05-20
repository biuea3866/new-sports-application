package com.sportsapp.domain.ticketing

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface EventRepository {
    fun save(event: Event): Event
    fun findById(id: Long): Event?
    fun softDelete(id: Long, deletedBy: Long?)
    fun findByOwnerId(ownerId: Long, pageable: Pageable): Page<Event>
    fun findByIdAndOwnerId(id: Long, ownerId: Long): Event?
}
