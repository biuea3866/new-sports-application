package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.LeaveContextRoomCommand
import com.sportsapp.domain.message.service.RoomContextDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LeaveContextRoomUseCase(
    private val roomContextDomainService: RoomContextDomainService,
) {
    @Transactional
    fun execute(command: LeaveContextRoomCommand) {
        roomContextDomainService.leaveContext(
            contextType = command.contextType,
            contextId = command.contextId,
            userId = command.userId,
        )
    }
}
