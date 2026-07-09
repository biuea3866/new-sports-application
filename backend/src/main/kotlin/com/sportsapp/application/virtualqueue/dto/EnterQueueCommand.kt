package com.sportsapp.application.virtualqueue.dto

import com.sportsapp.domain.virtualqueue.vo.QueueTargetType

/** `EnterQueueUseCase`(후행 티켓) 실행 파라미터. */
data class EnterQueueCommand(
    val type: QueueTargetType,
    val targetId: Long,
    val userId: Long,
)
