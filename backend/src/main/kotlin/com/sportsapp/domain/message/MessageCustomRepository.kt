package com.sportsapp.domain.message

import java.time.ZonedDateTime

interface MessageCustomRepository {
    fun findByCursor(roomId: Long, before: ZonedDateTime?, pageSize: Int): List<Message>
}
