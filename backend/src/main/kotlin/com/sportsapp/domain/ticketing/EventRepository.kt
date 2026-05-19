package com.sportsapp.domain.ticketing

interface EventRepository {
    fun save(event: Event): Event
    fun findById(id: Long): Event?
    fun softDelete(id: Long, deletedBy: Long?)
}
