package com.sportsapp.application.message.usecase

import com.sportsapp.domain.message.entity.Message
import com.sportsapp.domain.message.service.MessageDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListMessagesUseCase(
    private val messageDomainService: MessageDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(roomId: Long, userId: Long, cursor: String?): List<Message> =
        messageDomainService.listMessages(roomId, userId, cursor)
}
