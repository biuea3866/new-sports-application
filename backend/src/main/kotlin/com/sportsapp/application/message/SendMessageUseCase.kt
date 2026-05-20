package com.sportsapp.application.message

import com.sportsapp.domain.message.MessageDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SendMessageUseCase(
    private val messageDomainService: MessageDomainService,
) {
    @Transactional
    fun execute(command: SendMessageCommand): MessageResponse {
        val message = messageDomainService.sendMessage(command.roomId, command.senderId, command.content)
        return MessageResponse.of(message)
    }
}
