package com.sportsapp.application.message.usecase

import com.sportsapp.domain.message.service.RoomDomainService
import com.sportsapp.domain.message.vo.RoomListView
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListMyRoomsUseCase(
    private val roomDomainService: RoomDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(userId: Long, keyword: String?): List<RoomListView> =
        roomDomainService.findMyRoomViews(userId, keyword)
}
