package com.sportsapp.domain.message.entity

import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.message.vo.RoomType
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "rooms")
class Room(
    @Column(name = "type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    val type: RoomType,
    @Column(name = "name", length = 100)
    var name: String?,
    @Column(name = "context_type", length = 30)
    @Enumerated(EnumType.STRING)
    val contextType: RoomContextType?,
    @Column(name = "context_id")
    val contextId: Long?,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    @Column(name = "last_message_at")
    var lastMessageAt: ZonedDateTime? = null
        private set

    @OneToMany(mappedBy = "room", cascade = [CascadeType.PERSIST, CascadeType.MERGE], fetch = FetchType.LAZY)
    val messages: MutableList<Message> = mutableListOf()

    @OneToMany(mappedBy = "room", cascade = [CascadeType.PERSIST, CascadeType.MERGE], fetch = FetchType.LAZY)
    val participants: MutableList<RoomParticipant> = mutableListOf()

    fun validateNotDeleted() {
        check(!isDeleted) { "Room is already deleted" }
    }

    fun lastMessageBumpedTo(sentAt: ZonedDateTime) {
        lastMessageAt = sentAt
    }

    fun belongsToContext(): Boolean = contextType != null && contextId != null

    companion object {
        fun createDirect(): Room = Room(type = RoomType.DIRECT, name = null, contextType = null, contextId = null)

        fun createGroup(name: String): Room {
            require(name.isNotBlank()) { "Group room name must not be blank" }
            return Room(type = RoomType.GROUP, name = name, contextType = null, contextId = null)
        }

        fun createForContext(
            type: RoomType,
            contextType: RoomContextType,
            contextId: Long,
            name: String?,
        ): Room {
            if (name != null) {
                require(name.isNotBlank()) { "Group room name must not be blank" }
            }
            return Room(type = type, name = name, contextType = contextType, contextId = contextId)
        }
    }
}
