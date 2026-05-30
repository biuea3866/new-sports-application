package com.sportsapp.domain.operator.inbox

enum class OperatorInboxNotificationStatus {
    UNREAD,
    READ,
    ARCHIVED,
    ;

    fun canMarkRead(): Boolean = this == UNREAD
    fun canArchive(): Boolean = this != ARCHIVED
}
