package com.sportsapp.domain.ticketing.repository

import com.sportsapp.domain.ticketing.dto.EventCriteria
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.EventStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface EventCustomRepository {
    fun findByCriteria(criteria: EventCriteria, pageable: Pageable): Page<Event>
    fun countByOwnerIdGroupByStatus(ownerId: Long): Map<EventStatus, Long>
}
