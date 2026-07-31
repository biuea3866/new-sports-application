package com.sportsapp.application.message.usecase

import com.sportsapp.domain.message.entity.Message
import com.sportsapp.domain.message.service.MessageBackfillDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BackfillMessagesUseCase(
    private val messageBackfillDomainService: MessageBackfillDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(roomId: Long, userId: Long, afterMessageId: Long): List<Message> =
        messageBackfillDomainService.backfill(roomId, userId, afterMessageId)
}
