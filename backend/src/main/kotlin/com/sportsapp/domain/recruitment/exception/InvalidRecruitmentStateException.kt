package com.sportsapp.domain.recruitment.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus
import com.sportsapp.domain.recruitment.vo.RecruitmentStatus

class InvalidRecruitmentStateException(
    from: RecruitmentStatus,
    to: RecruitmentStatus,
) : BusinessException(
    errorCode = "INVALID_RECRUITMENT_STATE",
    message = "Cannot transit recruitment from $from to $to",
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
