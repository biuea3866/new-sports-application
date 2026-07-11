package com.sportsapp.application.virtualqueue.usecase

import com.sportsapp.application.virtualqueue.dto.GetQueueStatsCommand
import com.sportsapp.application.virtualqueue.dto.QueueStatsResponse
import com.sportsapp.domain.virtualqueue.service.VirtualQueueDomainService
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import org.springframework.stereotype.Service

/**
 * 운영자 통계 조회(FR-11) — `VirtualQueueDomainService.stats`만 위임한다.
 * Redis 기반이라 `@Transactional` 불요(BE-06 티켓 명시).
 *
 * stats 산출 방침(BE-04가 남긴 미결 확정): waitingCount·admittedCount는 Store 즉시 조회값,
 * admissionRatePerSec·avgWaitSeconds·p95WaitSeconds는 BE-10 Observability(Micrometer)가 소유한
 * 지표성 값이라 이 UseCase가 직접 계산하지 않는다 — `VirtualQueueDomainService.stats`가 반환하는
 * `QueueStats`(BE-10 미연동 상태에서는 0.0 placeholder)를 그대로 통과시킨다.
 */
@Service
class GetQueueStatsUseCase(
    private val virtualQueueDomainService: VirtualQueueDomainService,
) {
    fun execute(command: GetQueueStatsCommand): QueueStatsResponse {
        val target = QueueTarget(type = command.type, targetId = command.targetId)
        val stats = virtualQueueDomainService.stats(target)
        return QueueStatsResponse.of(stats)
    }
}
