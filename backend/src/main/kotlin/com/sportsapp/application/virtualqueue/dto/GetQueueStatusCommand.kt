package com.sportsapp.application.virtualqueue.dto

import com.sportsapp.domain.virtualqueue.vo.QueueTargetType

/** `GetQueueStatusUseCase`(후행 티켓) 실행 파라미터 — 폴링/heartbeat 조회 겸용. */
data class GetQueueStatusCommand(
    val type: QueueTargetType,
    val targetId: Long,
    val userId: Long,
)
