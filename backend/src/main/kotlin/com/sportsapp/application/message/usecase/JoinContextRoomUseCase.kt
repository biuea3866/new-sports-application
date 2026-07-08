package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.JoinContextRoomCommand
import com.sportsapp.domain.message.service.RoomContextDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class JoinContextRoomUseCase(
    private val roomContextDomainService: RoomContextDomainService,
) {
    @Transactional
    fun execute(command: JoinContextRoomCommand) {
        roomContextDomainService.joinContext(
            contextType = command.contextType,
            contextId = command.contextId,
            userId = command.userId,
        )
    }
}
