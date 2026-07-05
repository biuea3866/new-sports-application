package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.RoomUnreadResponse
import com.sportsapp.domain.message.service.ReadCursorDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MarkReadUseCase(
    private val readCursorDomainService: ReadCursorDomainService,
) {
    @Transactional
    fun execute(roomId: Long, userId: Long, lastReadMessageId: Long): RoomUnreadResponse {
        readCursorDomainService.markRead(roomId, userId, lastReadMessageId)
        val unreadCount = readCursorDomainService.unreadCount(roomId, userId)
        return RoomUnreadResponse(roomId = roomId, unreadCount = unreadCount)
    }
}
