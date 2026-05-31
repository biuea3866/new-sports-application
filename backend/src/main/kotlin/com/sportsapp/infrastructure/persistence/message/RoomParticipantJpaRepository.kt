package com.sportsapp.infrastructure.persistence.message

import com.sportsapp.domain.message.RoomParticipant
import org.springframework.data.jpa.repository.JpaRepository

interface RoomParticipantJpaRepository : JpaRepository<RoomParticipant, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): RoomParticipant?
}
