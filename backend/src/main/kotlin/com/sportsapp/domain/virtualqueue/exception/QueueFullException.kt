package com.sportsapp.domain.virtualqueue.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus
import com.sportsapp.domain.virtualqueue.vo.QueueTarget

/**
 * 대상 대기열이 포화(`ZCARD >= maxCapacity`) 상태일 때 신규 진입을 시도하면 던진다 (FR-7).
 * `VirtualQueueStore.enterIfAbsent`가 null을 반환하는 경우에 해당한다.
 */
class QueueFullException(target: QueueTarget) : BusinessException(
    errorCode = "QUEUE_FULL",
    message = "대기열이 가득 찼습니다: type=${target.type}, targetId=${target.targetId}",
) {
    override val status: ErrorStatus = ErrorStatus.TOO_MANY_REQUESTS
}
