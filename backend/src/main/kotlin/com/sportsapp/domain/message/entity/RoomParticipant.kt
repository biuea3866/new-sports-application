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
import java.time.ZonedDateTime

@Entity
@Table(name = "room_participants")
class RoomParticipant(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    val room: Room,
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
        fun create(room: Room, userId: Long): RoomParticipant =
            RoomParticipant(
                room = room,
                userId = userId,
                joinedAt = ZonedDateTime.now(),
            )
    }
}
