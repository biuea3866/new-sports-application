package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.ProvisionContextRoomCommand
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.service.RoomContextDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProvisionContextRoomUseCase(
    private val roomContextDomainService: RoomContextDomainService,
) {
    @Transactional
    fun execute(command: ProvisionContextRoomCommand): Room = roomContextDomainService.provision(
        contextType = command.contextType,
        contextId = command.contextId,
        name = command.name,
        hostUserId = command.hostUserId,
    )
}
