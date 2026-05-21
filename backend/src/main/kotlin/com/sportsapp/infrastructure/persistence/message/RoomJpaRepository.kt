package com.sportsapp.infrastructure.persistence.message

import com.sportsapp.domain.message.Room
import org.springframework.data.jpa.repository.JpaRepository

interface RoomJpaRepository : JpaRepository<Room, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Room?
}
