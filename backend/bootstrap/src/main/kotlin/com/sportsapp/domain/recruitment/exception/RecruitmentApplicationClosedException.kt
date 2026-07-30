package com.sportsapp.domain.recruitment.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class RecruitmentApplicationClosedException(recruitmentId: Long) : BusinessException(
    errorCode = "RECRUITMENT_APPLICATION_CLOSED",
    message = "Recruitment $recruitmentId 은(는) 신청 마감 시각이 지나 신청할 수 없습니다.",
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
