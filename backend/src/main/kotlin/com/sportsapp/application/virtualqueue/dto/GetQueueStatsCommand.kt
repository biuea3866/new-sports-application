package com.sportsapp.application.virtualqueue.dto

import com.sportsapp.domain.virtualqueue.vo.QueueTargetType

/** `GetQueueStatsUseCase`(후행 티켓) 실행 파라미터 — 운영자 통계 조회(FR-11). */
data class GetQueueStatsCommand(
    val type: QueueTargetType,
    val targetId: Long,
)
