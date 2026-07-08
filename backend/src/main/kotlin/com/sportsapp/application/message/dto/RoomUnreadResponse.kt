package com.sportsapp.application.message.dto

/** `/rooms/me/unread`, `POST /rooms/{id}/read` 응답 (TDD 응답 DTO 표, FR-9). */
data class RoomUnreadResponse(
    val roomId: Long,
    val unreadCount: Long,
)
