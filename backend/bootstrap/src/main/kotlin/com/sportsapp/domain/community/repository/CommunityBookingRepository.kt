package com.sportsapp.domain.community.repository

import com.sportsapp.domain.community.entity.CommunityBooking

interface CommunityBookingRepository {
    fun save(booking: CommunityBooking): CommunityBooking
    fun findBy(communityId: Long, slotId: Long): CommunityBooking?
    fun findAllBy(communityId: Long): List<CommunityBooking>
}
