package com.sportsapp.domain.recruitment.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class RecruitmentBusyException(recruitmentId: Long) : BusinessException(
    errorCode = "RECRUITMENT_BUSY",
    message = "Recruitment $recruitmentId is currently locked by another request. Please try again.",
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
