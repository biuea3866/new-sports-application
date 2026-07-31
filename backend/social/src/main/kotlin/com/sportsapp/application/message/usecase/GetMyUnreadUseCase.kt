package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.RoomUnreadResponse
import com.sportsapp.domain.message.service.ReadCursorDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetMyUnreadUseCase(
    private val readCursorDomainService: ReadCursorDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(userId: Long): List<RoomUnreadResponse> =
        readCursorDomainService.unreadForMyRooms(userId)
            .map { (roomId, unreadCount) -> RoomUnreadResponse(roomId = roomId, unreadCount = unreadCount) }
}
