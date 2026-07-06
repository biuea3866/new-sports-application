package com.sportsapp.domain.recruitment.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class RecruitmentFullException(recruitmentId: Long) : BusinessException(
    errorCode = "RECRUITMENT_FULL",
    message = "Recruitment $recruitmentId has no remaining capacity.",
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
