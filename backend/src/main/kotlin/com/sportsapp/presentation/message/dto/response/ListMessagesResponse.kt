package com.sportsapp.presentation.message.dto.response

import com.sportsapp.domain.message.entity.Message

data class ListMessagesResponse(
    val messages: List<MessageResponse>,
    val nextCursor: String?,
) {
    companion object {
        fun of(messages: List<Message>, pageSize: Int): ListMessagesResponse {
            val hasNextPage = messages.size == pageSize
            val nextCursor = if (hasNextPage) messages.last().createdAt.toString() else null
            return ListMessagesResponse(
                messages = messages.map { MessageResponse.of(it) },
                nextCursor = nextCursor,
            )
        }
    }
}
