package com.sportsapp.domain.user.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class InvalidNicknameException(nickname: String) : BusinessException(
    errorCode = "INVALID_NICKNAME",
    message = "Invalid nickname: $nickname",
) {
    override val status: ErrorStatus = ErrorStatus.BAD_REQUEST
}
