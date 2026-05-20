package com.sportsapp.domain.message

interface RoomRepository {
    fun save(room: Room): Room
    fun findById(id: Long): Room?
}
