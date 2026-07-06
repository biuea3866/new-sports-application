package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.ProvisionContextRoomCommand
import com.sportsapp.domain.message.service.RoomContextDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProvisionContextRoomUseCase(
    private val roomContextDomainService: RoomContextDomainService,
) {
    /** 호출부(`CommunityChatIntegrationEventWorker`)가 결과 방을 사용하지 않아 반환 타입을 `Unit`으로 좁힌다 (PR #270 리뷰 p4). */
    @Transactional
    fun execute(command: ProvisionContextRoomCommand) {
        roomContextDomainService.provision(
            contextType = command.contextType,
            contextId = command.contextId,
            name = command.name,
            hostUserId = command.hostUserId,
        )
    }
}
