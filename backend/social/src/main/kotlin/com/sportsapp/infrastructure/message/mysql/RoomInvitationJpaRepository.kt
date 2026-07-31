package com.sportsapp.infrastructure.message.mysql

import com.sportsapp.domain.message.entity.RoomInvitation
import org.springframework.data.jpa.repository.JpaRepository

interface RoomInvitationJpaRepository : JpaRepository<RoomInvitation, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): RoomInvitation?
}
