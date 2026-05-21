package com.sportsapp.application.message

import com.sportsapp.domain.message.MessageDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListMyRoomsUseCase(
    private val messageDomainService: MessageDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(userId: Long, keyword: String?): List<RoomResponse> {
        return messageDomainService.findMyRooms(userId, keyword)
            .map { RoomResponse.of(it) }
    }
}
