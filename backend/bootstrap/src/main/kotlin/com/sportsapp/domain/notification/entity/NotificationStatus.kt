package com.sportsapp.domain.notification.entity
enum class NotificationStatus {
    QUEUED,
    SENT,
    FAILED;

    fun canTransitToSent(): Boolean = this == QUEUED

    fun canTransitToFailed(): Boolean = this != FAILED
}
