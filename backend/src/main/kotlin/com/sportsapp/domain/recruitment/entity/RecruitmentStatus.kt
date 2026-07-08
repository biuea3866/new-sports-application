package com.sportsapp.domain.recruitment.entity

enum class RecruitmentStatus {
    OPEN,
    CLOSED,
    CANCELLED;

    fun canTransitTo(target: RecruitmentStatus): Boolean = when (this) {
        OPEN -> target == CLOSED || target == CANCELLED
        CLOSED -> target == CANCELLED
        CANCELLED -> false
    }
}
