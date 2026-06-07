package com.sportsapp.domain.message.repository

import com.sportsapp.domain.message.entity.Message
import java.time.ZonedDateTime

interface MessageCustomRepository {
    fun findByRoomIdAndNotDeleted(roomId: Long): List<Message>
    fun findByCursor(roomId: Long, before: ZonedDateTime?, pageSize: Int): List<Message>
    fun softDeleteAllByRoomId(roomId: Long, userId: Long?)
}
