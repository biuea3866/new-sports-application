package com.sportsapp.infrastructure.persistence.ticketing

import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface EventJpaRepository : JpaRepository<Event, Long>, EventQueryDslRepository {
    fun findByIdAndDeletedAtIsNull(id: Long): Event?
    fun findByOwnerIdAndDeletedAtIsNull(ownerId: Long, pageable: Pageable): Page<Event>
    fun findByOwnerIdAndStatusAndDeletedAtIsNull(ownerId: Long, status: EventStatus, pageable: Pageable): Page<Event>
    fun findByIdAndOwnerIdAndDeletedAtIsNull(id: Long, ownerId: Long): Event?
}
