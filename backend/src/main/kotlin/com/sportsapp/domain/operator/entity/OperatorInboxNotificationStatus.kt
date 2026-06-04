package com.sportsapp.domain.operator.entity

enum class OperatorInboxNotificationStatus {
    UNREAD,
    READ,
    ARCHIVED,
    ;

    fun canMarkRead(): Boolean = this == UNREAD
    fun canArchive(): Boolean = this != ARCHIVED
}
