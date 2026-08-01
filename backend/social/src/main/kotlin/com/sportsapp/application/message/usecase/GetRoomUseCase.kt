package com.sportsapp.application.message.usecase

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.service.RoomDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetRoomUseCase(
    private val roomDomainService: RoomDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(roomId: Long, userId: Long): Room =
        roomDomainService.getRoom(roomId, userId)
}
