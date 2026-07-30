package com.sportsapp.domain.facility.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class UnauthorizedProgramAccessException(programId: Long, userId: Long) : BusinessException(
    errorCode = "UNAUTHORIZED_PROGRAM_ACCESS",
    message = "User($userId) is not the owner of program($programId)",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
