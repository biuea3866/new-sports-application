package com.sportsapp.infrastructure.community.mysql

import com.sportsapp.domain.community.entity.CommunityBooking
import org.springframework.data.jpa.repository.JpaRepository

interface CommunityBookingJpaRepository : JpaRepository<CommunityBooking, Long> {
    fun findByCommunityIdAndSlotIdAndDeletedAtIsNull(communityId: Long, slotId: Long): CommunityBooking?
    fun findAllByCommunityIdAndDeletedAtIsNull(communityId: Long): List<CommunityBooking>
}
