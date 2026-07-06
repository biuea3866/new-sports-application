package com.sportsapp.domain.message.repository

import com.sportsapp.domain.message.entity.RoomInvitation

interface RoomInvitationRepository {
    fun save(invitation: RoomInvitation): RoomInvitation
    fun findById(id: Long): RoomInvitation?
    fun findPendingBy(roomId: Long, inviteeUserId: Long): RoomInvitation?
    fun findPendingByInvitee(inviteeUserId: Long): List<RoomInvitation>
}
