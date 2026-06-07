package com.sportsapp.application.message.usecase

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.service.MessageDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListMyRoomsUseCase(
    private val messageDomainService: MessageDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(userId: Long, keyword: String?): List<Room> =
        messageDomainService.findMyRooms(userId, keyword)
}
