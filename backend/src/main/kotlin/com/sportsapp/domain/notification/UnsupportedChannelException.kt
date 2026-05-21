package com.sportsapp.domain.notification

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class UnsupportedChannelException(
    channel: NotificationChannel,
) : BusinessException(
    errorCode = "UNSUPPORTED_CHANNEL",
    message = "지원하지 않는 채널입니다: $channel",
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
