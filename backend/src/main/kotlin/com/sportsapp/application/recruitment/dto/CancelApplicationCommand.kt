package com.sportsapp.application.recruitment.dto

data class CancelApplicationCommand(
    val applicationId: Long,
    val applicantUserId: Long,
)
