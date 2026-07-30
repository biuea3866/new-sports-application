package com.sportsapp.domain.recruitment.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class NotRecruiterException(recruitmentId: Long) : BusinessException(
    errorCode = "NOT_RECRUITER",
    message = "Recruitment $recruitmentId 의 개설자만 수행할 수 있는 작업입니다.",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
