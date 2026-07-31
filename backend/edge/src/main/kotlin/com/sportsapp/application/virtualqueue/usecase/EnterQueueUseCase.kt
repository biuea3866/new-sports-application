package com.sportsapp.application.virtualqueue.usecase

import com.sportsapp.application.virtualqueue.dto.EnterQueueCommand
import com.sportsapp.application.virtualqueue.dto.QueueEntryResponse
import com.sportsapp.domain.virtualqueue.service.VirtualQueueDomainService
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import org.springframework.stereotype.Service

/**
 * 대기열 진입(FR-2·FR-7) — `VirtualQueueDomainService.enter`만 위임한다.
 * Redis 기반 상태(큐 진입·순번·admission)라 `@Transactional` 불요(BE-06 티켓 명시).
 */
@Service
class EnterQueueUseCase(
    private val virtualQueueDomainService: VirtualQueueDomainService,
) {
    fun execute(command: EnterQueueCommand): QueueEntryResponse {
        val target = QueueTarget(type = command.type, targetId = command.targetId)
        val status = virtualQueueDomainService.enter(target, command.userId)
        return QueueEntryResponse.of(status)
    }
}
