package com.sportsapp.application.message

import com.sportsapp.domain.message.MessageDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListMessagesUseCase(
    private val messageDomainService: MessageDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(roomId: Long, userId: Long, cursor: String?): ListMessagesResponse {
        val messages = messageDomainService.listMessages(roomId, userId, cursor)
        return ListMessagesResponse.of(messages, PAGE_SIZE)
    }

    companion object {
        private const val PAGE_SIZE = 30
    }
}
