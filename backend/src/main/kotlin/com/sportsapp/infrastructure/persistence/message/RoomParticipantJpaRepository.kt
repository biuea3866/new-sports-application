package com.sportsapp.infrastructure.persistence.message

import com.sportsapp.domain.message.RoomParticipant
import org.springframework.data.jpa.repository.JpaRepository

interface RoomParticipantJpaRepository : JpaRepository<RoomParticipant, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): RoomParticipant?
    fun findByRoomIdAndDeletedAtIsNull(roomId: Long): List<RoomParticipant>
    fun existsByRoomIdAndUserIdAndDeletedAtIsNull(roomId: Long, userId: Long): Boolean
    fun findByRoomIdAndUserIdAndDeletedAtIsNull(roomId: Long, userId: Long): RoomParticipant?
}
