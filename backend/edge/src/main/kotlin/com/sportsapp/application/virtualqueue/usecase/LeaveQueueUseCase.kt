package com.sportsapp.application.virtualqueue.usecase

import com.sportsapp.application.virtualqueue.dto.LeaveQueueCommand
import com.sportsapp.domain.virtualqueue.service.VirtualQueueDomainService
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import org.springframework.stereotype.Service

/**
 * 명시적 이탈 — `VirtualQueueDomainService.leave`만 위임한다.
 * Redis 기반이라 `@Transactional` 불요(BE-06 티켓 명시).
 */
@Service
class LeaveQueueUseCase(
    private val virtualQueueDomainService: VirtualQueueDomainService,
) {
    fun execute(command: LeaveQueueCommand) {
        val target = QueueTarget(type = command.type, targetId = command.targetId)
        virtualQueueDomainService.leave(target, command.userId)
    }
}
