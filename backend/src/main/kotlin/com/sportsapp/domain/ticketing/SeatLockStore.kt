package com.sportsapp.domain.ticketing

import java.time.Duration

interface SeatLockStore {
    fun tryLock(eventId: Long, seatId: Long, userId: Long, ttl: Duration): Boolean
    fun unlock(eventId: Long, seatId: Long, userId: Long): Boolean
}
