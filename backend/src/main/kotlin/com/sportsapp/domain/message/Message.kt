package com.sportsapp.domain.message

import com.sportsapp.domain.common.JpaAuditingBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "messages")
class Message(
    @Column(name = "room_id", nullable = false)
    val roomId: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    val content: String,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    companion object {
        fun create(roomId: Long, userId: Long, content: String): Message {
            require(content.isNotBlank()) { "Message content must not be blank" }
            return Message(roomId = roomId, userId = userId, content = content)
        }
    }
}
