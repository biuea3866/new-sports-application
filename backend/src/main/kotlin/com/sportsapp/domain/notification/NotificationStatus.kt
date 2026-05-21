package com.sportsapp.domain.notification

enum class NotificationStatus {
    QUEUED,
    SENT,
    FAILED;

    fun canTransitToSent(): Boolean = this == QUEUED
}
