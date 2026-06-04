package com.sportsapp.domain.ticketing.gateway

import java.time.Duration

interface SeatLockStore {
    fun tryLock(eventId: Long, seatId: Long, userId: Long, ttl: Duration): Boolean
    fun unlock(eventId: Long, seatId: Long, userId: Long): Boolean
    fun getOwner(eventId: Long, seatId: Long): Long?
}
