package com.sportsapp.infrastructure.lock

import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.ticketing.SeatLockStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class SeatLockStoreImpl(
    private val distributedLock: DistributedLock,
    private val redisTemplate: StringRedisTemplate,
) : SeatLockStore {

    override fun tryLock(eventId: Long, seatId: Long, userId: Long, ttl: Duration): Boolean =
        distributedLock.tryLock(lockKey(eventId, seatId), userId.toString(), ttl)

    override fun unlock(eventId: Long, seatId: Long, userId: Long): Boolean =
        distributedLock.unlock(lockKey(eventId, seatId), userId.toString())

    override fun getOwner(eventId: Long, seatId: Long): Long? =
        redisTemplate.opsForValue().get(lockKey(eventId, seatId))?.toLongOrNull()

    private fun lockKey(eventId: Long, seatId: Long) = "seat:lock:$eventId:$seatId"
}
