package com.sportsapp.application.recruitment.dto

data class CancelRecruitmentCommand(
    val recruitmentId: Long,
    val recruiterUserId: Long,
)
