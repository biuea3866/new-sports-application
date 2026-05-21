package com.sportsapp.infrastructure.persistence.message

import com.sportsapp.domain.message.MessageCustomRepository
import com.sportsapp.domain.message.Message
import com.sportsapp.domain.message.MessageRepository
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class MessageRepositoryImpl(
    private val messageJpaRepository: MessageJpaRepository,
    private val lMessageCustomRepository: MessageCustomRepository,
) : MessageRepository {

    override fun save(message: Message): Message = messageJpaRepository.save(message)

    override fun findById(id: Long): Message? = messageJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findByRoomId(roomId: Long): List<Message> =
        messageJpaRepository.findByRoomIdAndDeletedAtIsNull(roomId)

    override fun findByCursor(roomId: Long, before: ZonedDateTime?, pageSize: Int): List<Message> =
        lMessageCustomRepository.findByCursor(roomId, before, pageSize)

    override fun softDeleteAllByRoomId(roomId: Long, userId: Long?) {
        val messages = messageJpaRepository.findByRoomIdAndDeletedAtIsNull(roomId)
        messages.forEach { it.softDelete(userId) }
        messageJpaRepository.saveAll(messages)
    }
}
