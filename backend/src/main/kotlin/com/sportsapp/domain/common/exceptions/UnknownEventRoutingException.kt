package com.sportsapp.domain.common.exceptions

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class UnknownEventRoutingException(eventType: String) : BusinessException(
    errorCode = "UNKNOWN_EVENT_ROUTING",
    message = "라우팅 대상을 알 수 없는 도메인 이벤트 타입입니다: $eventType"
) {
    override val status: ErrorStatus = ErrorStatus.INTERNAL
}
