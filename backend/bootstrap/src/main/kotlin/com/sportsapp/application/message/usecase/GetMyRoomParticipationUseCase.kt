package com.sportsapp.application.message.usecase

import com.sportsapp.domain.message.service.RoomParticipationQueryService
import com.sportsapp.domain.message.vo.RoomParticipationView
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetMyRoomParticipationUseCase(
    private val roomParticipationQueryService: RoomParticipationQueryService,
) {
    @Transactional(readOnly = true)
    fun execute(roomId: Long, userId: Long): RoomParticipationView =
        roomParticipationQueryService.getMyParticipation(roomId, userId)
}
