package com.sportsapp.infrastructure.persistence.ticketing

import com.sportsapp.domain.ticketing.EventStatus

interface EventQueryDslRepository {
    fun countByOwnerIdGroupByStatus(ownerId: Long): Map<EventStatus, Long>
}
