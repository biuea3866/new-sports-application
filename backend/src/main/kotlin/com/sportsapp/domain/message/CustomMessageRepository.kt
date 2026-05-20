package com.sportsapp.domain.message

import java.time.ZonedDateTime

interface CustomMessageRepository {
    fun findByCursor(roomId: Long, before: ZonedDateTime?, pageSize: Int): List<Message>
}
