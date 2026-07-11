package com.sportsapp.domain.virtualqueue.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus
import com.sportsapp.domain.virtualqueue.vo.QueueTarget

/**
 * 큐에 존재하지 않는(이탈했거나 애초에 진입하지 않은) 사용자가 상태를 조회하면 던진다.
 * `VirtualQueueStore.seqOf`가 null을 반환하는 경우에 해당한다 — FE 계약상 404로 매핑되어
 * 재진입을 유도한다.
 */
class QueueEntryNotFoundException(target: QueueTarget, userId: Long) : BusinessException(
    errorCode = "QUEUE_ENTRY_NOT_FOUND",
    message = "대기열에 존재하지 않는 사용자입니다: type=${target.type}, targetId=${target.targetId}, userId=$userId",
) {
    override val status: ErrorStatus = ErrorStatus.NOT_FOUND
}
