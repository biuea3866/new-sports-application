package com.sportsapp.domain.recruitment.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus
import com.sportsapp.domain.recruitment.entity.RecruitmentStatus

class RecruitmentNotOpenException(
    recruitmentId: Long,
    status: RecruitmentStatus,
) : BusinessException(
    errorCode = "RECRUITMENT_NOT_OPEN",
    message = "Recruitment $recruitmentId 은(는) 모집 중(OPEN) 상태가 아닙니다. 현재 상태: $status",
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
