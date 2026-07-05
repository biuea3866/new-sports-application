package com.sportsapp.domain.message.repository

import com.sportsapp.domain.message.entity.Message
import java.time.ZonedDateTime

interface MessageCustomRepository {
    fun findByRoomIdAndNotDeleted(roomId: Long): List<Message>
    fun findByCursor(roomId: Long, before: ZonedDateTime?, pageSize: Int): List<Message>
    fun softDeleteAllByRoomId(roomId: Long, userId: Long?)

    /** 읽음 커서 이후(id > afterMessageId), 본인이 아닌(excludeUserId 제외), 삭제되지 않은 메시지 수 (FR-9). */
    fun countUnread(roomId: Long, afterMessageId: Long, excludeUserId: Long): Long
}
