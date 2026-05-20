package com.sportsapp.infrastructure.persistence.ticketing

import com.sportsapp.domain.ticketing.Event
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface EventJpaRepository : JpaRepository<Event, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Event?
    fun findByOwnerIdAndDeletedAtIsNull(ownerId: Long, pageable: Pageable): Page<Event>
    fun findByIdAndOwnerIdAndDeletedAtIsNull(id: Long, ownerId: Long): Event?
}
