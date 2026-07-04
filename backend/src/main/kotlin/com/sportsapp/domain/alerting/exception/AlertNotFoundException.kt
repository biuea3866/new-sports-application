package com.sportsapp.domain.alerting.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * alertId로 [com.sportsapp.domain.alerting.entity.Alert]를 찾을 수 없을 때 던진다.
 */
class AlertNotFoundException(alertId: Long) : BusinessException(
    errorCode = "ALERT_NOT_FOUND",
    message = "알림을 찾을 수 없습니다: $alertId",
) {
    override val status: ErrorStatus = ErrorStatus.NOT_FOUND
}
