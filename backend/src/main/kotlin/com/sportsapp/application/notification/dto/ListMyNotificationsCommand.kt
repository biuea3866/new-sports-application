package com.sportsapp.application.notification

data class ListMyNotificationsCommand(
    val userId: Long,
    val onlyUnread: Boolean,
    val page: Int,
    val size: Int,
)
