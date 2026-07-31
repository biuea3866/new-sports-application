package com.sportsapp.application.recruitment.dto

import java.math.BigDecimal
import java.time.ZonedDateTime

data class CreateRecruitmentCommand(
    val title: String,
    val description: String?,
    val capacity: Int,
    val feeAmount: BigDecimal,
    val activityAt: ZonedDateTime,
    val applicationDeadline: ZonedDateTime,
    val communityId: Long?,
    val recruiterUserId: Long,
)
