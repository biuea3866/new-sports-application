package com.sportsapp.domain.message.repository

import com.sportsapp.domain.message.entity.RoomInvitation

interface RoomInvitationCustomRepository {
    fun findPendingBy(roomId: Long, inviteeUserId: Long): RoomInvitation?
    fun findPendingByInvitee(inviteeUserId: Long): List<RoomInvitation>
}
