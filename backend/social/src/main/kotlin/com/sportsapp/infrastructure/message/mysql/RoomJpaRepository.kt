package com.sportsapp.infrastructure.message.mysql

import com.sportsapp.domain.message.entity.Room
import org.springframework.data.jpa.repository.JpaRepository

interface RoomJpaRepository : JpaRepository<Room, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Room?
}
