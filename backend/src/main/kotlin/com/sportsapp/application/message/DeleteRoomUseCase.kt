package com.sportsapp.application.message

import com.sportsapp.domain.message.MessageDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeleteRoomUseCase(
    private val messageDomainService: MessageDomainService,
) {
    @Transactional
    fun execute(roomId: Long, userId: Long) {
        messageDomainService.leaveRoom(roomId, userId)
    }
}
