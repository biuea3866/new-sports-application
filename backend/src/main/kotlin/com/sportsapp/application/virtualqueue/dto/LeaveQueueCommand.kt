package com.sportsapp.application.virtualqueue.dto

import com.sportsapp.domain.virtualqueue.vo.QueueTargetType

/** `LeaveQueueUseCase`(후행 티켓) 실행 파라미터 — 명시적 이탈. */
data class LeaveQueueCommand(
    val type: QueueTargetType,
    val targetId: Long,
    val userId: Long,
)
