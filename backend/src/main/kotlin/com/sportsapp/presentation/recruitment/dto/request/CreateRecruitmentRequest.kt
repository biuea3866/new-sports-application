package com.sportsapp.presentation.recruitment.dto.request

import com.sportsapp.application.recruitment.dto.CreateRecruitmentCommand
import java.math.BigDecimal
import java.time.ZonedDateTime

data class CreateRecruitmentRequest(
    val title: String,
    val description: String?,
    val capacity: Int,
    val feeAmount: BigDecimal,
    val activityAt: ZonedDateTime,
    val applicationDeadline: ZonedDateTime,
    val communityId: Long?,
) {
    fun toCommand(recruiterUserId: Long): CreateRecruitmentCommand = CreateRecruitmentCommand(
        title = title,
        description = description,
        capacity = capacity,
        feeAmount = feeAmount,
        activityAt = activityAt,
        applicationDeadline = applicationDeadline,
        communityId = communityId,
        recruiterUserId = recruiterUserId,
    )
}
