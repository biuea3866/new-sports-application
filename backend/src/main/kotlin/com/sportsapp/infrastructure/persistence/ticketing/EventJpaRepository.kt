package com.sportsapp.infrastructure.persistence.ticketing

import com.sportsapp.domain.ticketing.Event
import org.springframework.data.jpa.repository.JpaRepository

interface EventJpaRepository : JpaRepository<Event, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Event?
}
