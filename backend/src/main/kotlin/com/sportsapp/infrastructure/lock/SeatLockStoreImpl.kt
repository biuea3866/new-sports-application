package com.sportsapp.infrastructure.lock

import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.ticketing.SeatLockStore
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class SeatLockStoreImpl(
    private val distributedLock: DistributedLock,
) : SeatLockStore {

    override fun tryLock(eventId: Long, seatId: Long, userId: Long, ttl: Duration): Boolean =
        distributedLock.tryLock(lockKey(eventId, seatId), userId.toString(), ttl)

    override fun unlock(eventId: Long, seatId: Long, userId: Long): Boolean =
        distributedLock.unlock(lockKey(eventId, seatId), userId.toString())

    private fun lockKey(eventId: Long, seatId: Long) = "seat:lock:$eventId:$seatId"
}
