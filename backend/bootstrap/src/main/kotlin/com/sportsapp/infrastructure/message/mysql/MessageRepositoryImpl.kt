package com.sportsapp.infrastructure.message.mysql

import com.sportsapp.domain.message.entity.Message
import com.sportsapp.domain.message.repository.MessageCustomRepository
import com.sportsapp.domain.message.repository.MessageRepository
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class MessageRepositoryImpl(
    private val messageJpaRepository: MessageJpaRepository,
    private val messageCustomRepository: MessageCustomRepository,
) : MessageRepository {

    override fun save(message: Message): Message = messageJpaRepository.save(message)

    override fun findById(id: Long): Message? = messageJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findByRoomId(roomId: Long): List<Message> =
        messageCustomRepository.findByRoomIdAndNotDeleted(roomId)

    override fun findByCursor(roomId: Long, before: ZonedDateTime?, pageSize: Int): List<Message> =
        messageCustomRepository.findByCursor(roomId, before, pageSize)

    override fun softDeleteAllByRoomId(roomId: Long, userId: Long?) =
        messageCustomRepository.softDeleteAllByRoomId(roomId, userId)
}
