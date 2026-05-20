package com.sportsapp.infrastructure.persistence.message

import com.sportsapp.domain.message.Message
import com.sportsapp.domain.message.MessageRepository
import org.springframework.stereotype.Component

@Component
class MessageRepositoryImpl(
    private val messageJpaRepository: MessageJpaRepository,
) : MessageRepository {

    override fun save(message: Message): Message = messageJpaRepository.save(message)

    override fun findById(id: Long): Message? = messageJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findByRoomId(roomId: Long): List<Message> =
        messageJpaRepository.findByRoomIdAndDeletedAtIsNull(roomId)
}
