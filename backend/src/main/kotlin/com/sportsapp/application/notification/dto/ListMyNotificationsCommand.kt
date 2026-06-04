package com.sportsapp.application.notification.dto
data class ListMyNotificationsCommand(
    val userId: Long,
    val onlyUnread: Boolean,
    val page: Int,
    val size: Int,
)
