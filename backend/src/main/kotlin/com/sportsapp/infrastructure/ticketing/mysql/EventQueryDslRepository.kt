package com.sportsapp.infrastructure.ticketing.mysql

import com.sportsapp.domain.ticketing.entity.EventStatus

interface EventQueryDslRepository {
    fun countByOwnerIdGroupByStatus(ownerId: Long): Map<EventStatus, Long>
}
