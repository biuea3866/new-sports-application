package com.sportsapp.domain.virtualqueue.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus
import com.sportsapp.domain.virtualqueue.vo.QueueTarget

/**
 * 구매 앞단 게이트([EntryTokenGateInterceptor], BE-09)에서 입장 토큰 검증에 실패했을 때 던진다
 * (FR-5, TDD "API 계약" 5번 — 대기열 우회 차단). `EntryTokenGuard.verify`가 false를 반환하는
 * 모든 경우(토큰 부재·위조·만료·userId 헤더 부재)에 해당한다.
 */
class QueueBypassDeniedException(target: QueueTarget) : BusinessException(
    errorCode = "QUEUE_BYPASS_DENIED",
    message = "입장 토큰 검증에 실패해 대기열 우회 요청을 거부했습니다: type=${target.type}, targetId=${target.targetId}",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
