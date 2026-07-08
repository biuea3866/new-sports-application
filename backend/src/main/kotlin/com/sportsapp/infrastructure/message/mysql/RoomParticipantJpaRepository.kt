package com.sportsapp.infrastructure.message.mysql

import com.sportsapp.domain.message.entity.RoomParticipant
import org.springframework.data.jpa.repository.JpaRepository

interface RoomParticipantJpaRepository : JpaRepository<RoomParticipant, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): RoomParticipant?
}
