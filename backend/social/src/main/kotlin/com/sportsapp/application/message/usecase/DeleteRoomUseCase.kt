package com.sportsapp.application.message.usecase

import com.sportsapp.domain.message.service.RoomDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeleteRoomUseCase(
    private val roomDomainService: RoomDomainService,
) {
    @Transactional
    fun execute(roomId: Long, userId: Long) {
        roomDomainService.leaveRoom(roomId, userId)
    }
}
