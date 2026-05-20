package com.sportsapp.domain.ticketing

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface CustomEventRepository {
    fun findByCriteria(criteria: EventCriteria, pageable: Pageable): Page<Event>
}
