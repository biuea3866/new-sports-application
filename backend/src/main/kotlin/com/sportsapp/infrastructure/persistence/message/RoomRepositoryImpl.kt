package com.sportsapp.infrastructure.persistence.message

import com.sportsapp.domain.message.Room
import com.sportsapp.domain.message.RoomRepository
import org.springframework.stereotype.Component

@Component
class RoomRepositoryImpl(
    private val roomJpaRepository: RoomJpaRepository,
) : RoomRepository {

    override fun save(room: Room): Room = roomJpaRepository.save(room)

    override fun findById(id: Long): Room? = roomJpaRepository.findByIdAndDeletedAtIsNull(id)
}
