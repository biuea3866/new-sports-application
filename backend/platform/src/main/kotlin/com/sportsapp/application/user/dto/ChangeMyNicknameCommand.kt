package com.sportsapp.application.user.dto

data class ChangeMyNicknameCommand(
    val userId: Long,
    val nickname: String,
)
