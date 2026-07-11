package com.sportsapp.application.virtualqueue.usecase

import com.sportsapp.application.virtualqueue.dto.GetQueueStatusCommand
import com.sportsapp.application.virtualqueue.dto.QueueEntryResponse
import com.sportsapp.domain.virtualqueue.service.VirtualQueueDomainService
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import org.springframework.stereotype.Service

/**
 * 순번·상태 조회(폴링+heartbeat 겸용) — `VirtualQueueDomainService.status`만 위임한다.
 * Redis 기반이라 `@Transactional` 불요(BE-06 티켓 명시).
 */
@Service
class GetQueueStatusUseCase(
    private val virtualQueueDomainService: VirtualQueueDomainService,
) {
    fun execute(command: GetQueueStatusCommand): QueueEntryResponse {
        val target = QueueTarget(type = command.type, targetId = command.targetId)
        val status = virtualQueueDomainService.status(target, command.userId)
        return QueueEntryResponse.of(status)
    }
}
