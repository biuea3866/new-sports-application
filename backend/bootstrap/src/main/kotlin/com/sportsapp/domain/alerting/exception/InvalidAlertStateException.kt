package com.sportsapp.domain.alerting.exception

import com.sportsapp.domain.alerting.entity.AlertStatus
import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * [AlertStatus.canTransitTo]가 거부하는 상태 전이를 시도하면 던진다
 * (예: DELIVERED → DELIVERED 재발송). InvalidLimitedDropStateException과 동일 패턴.
 */
class InvalidAlertStateException(
    from: AlertStatus,
    to: AlertStatus,
) : BusinessException(
    errorCode = "INVALID_ALERT_STATE",
    message = "알림 상태 전이 불가: $from → $to",
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
