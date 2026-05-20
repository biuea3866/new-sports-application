package com.sportsapp.domain.message

interface MessageRepository {
    fun save(message: Message): Message
    fun findById(id: Long): Message?
    fun findByRoomId(roomId: Long): List<Message>
}
