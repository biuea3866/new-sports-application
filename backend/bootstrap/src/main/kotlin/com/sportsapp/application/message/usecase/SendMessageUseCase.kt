package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.SendMessageCommand
import com.sportsapp.domain.message.entity.Message
import com.sportsapp.domain.message.service.MessageDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SendMessageUseCase(
    private val messageDomainService: MessageDomainService,
) {
    @Transactional
    fun execute(command: SendMessageCommand): Message =
        messageDomainService.sendMessage(command.roomId, command.senderId, command.content)
}
