package com.sportsapp.application.recruitment.dto

import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.entity.RecruitmentStatus
import java.math.BigDecimal
import java.time.ZonedDateTime

data class RecruitmentResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val capacity: Int,
    val feeAmount: BigDecimal,
    val activityAt: ZonedDateTime,
    val applicationDeadline: ZonedDateTime,
    val communityId: Long?,
    val recruiterUserId: Long,
    val status: RecruitmentStatus,
) {
    companion object {
        fun of(recruitment: Recruitment): RecruitmentResponse = RecruitmentResponse(
            id = recruitment.id,
            title = recruitment.title,
            description = recruitment.description,
            capacity = recruitment.capacity,
            feeAmount = recruitment.feeAmount,
            activityAt = recruitment.activityAt,
            applicationDeadline = recruitment.applicationDeadline,
            communityId = recruitment.communityId,
            recruiterUserId = recruitment.recruiterUserId,
            status = recruitment.status,
        )
    }
}
