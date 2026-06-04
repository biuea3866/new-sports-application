package com.sportsapp.domain.message

import java.time.ZonedDateTime

interface MessageCustomRepository {
    fun findByRoomIdAndNotDeleted(roomId: Long): List<Message>
    fun findByCursor(roomId: Long, before: ZonedDateTime?, pageSize: Int): List<Message>
    fun softDeleteAllByRoomId(roomId: Long, userId: Long?)
}
