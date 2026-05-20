package com.sportsapp.application.message

import com.sportsapp.domain.message.MessageDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetRoomUseCase(
    private val messageDomainService: MessageDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(roomId: Long, userId: Long): RoomResponse {
        val room = messageDomainService.getRoom(roomId, userId)
        return RoomResponse.of(room)
    }
}
