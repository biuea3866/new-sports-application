package com.sportsapp.domain.message

import com.sportsapp.domain.common.JpaAuditingBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "room_participants")
class RoomParticipant(
    @Column(name = "room_id", nullable = false)
    val roomId: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "joined_at", nullable = false)
    val joinedAt: ZonedDateTime,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    companion object {
        fun create(roomId: Long, userId: Long): RoomParticipant =
            RoomParticipant(
                roomId = roomId,
                userId = userId,
                joinedAt = ZonedDateTime.now(),
            )
    }
}
