package com.sportsapp.domain.message

import java.time.ZonedDateTime

interface MessageRepository {
    fun save(message: Message): Message
    fun findById(id: Long): Message?
    fun findByRoomId(roomId: Long): List<Message>
    fun findByCursor(roomId: Long, before: ZonedDateTime?, pageSize: Int): List<Message>
    fun softDeleteAllByRoomId(roomId: Long, userId: Long?)
}
