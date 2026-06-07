package com.sportsapp.domain.message.entity

import com.sportsapp.domain.common.JpaAuditingBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "messages")
class Message(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    val room: Room,
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
        fun create(room: Room, userId: Long, content: String): Message {
            require(content.isNotBlank()) { "Message content must not be blank" }
            return Message(room = room, userId = userId, content = content)
        }
    }
}
