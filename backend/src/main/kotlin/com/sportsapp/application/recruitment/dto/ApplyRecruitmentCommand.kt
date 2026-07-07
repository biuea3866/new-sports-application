package com.sportsapp.application.recruitment.dto

data class ApplyRecruitmentCommand(
    val recruitmentId: Long,
    val applicantUserId: Long,
)
