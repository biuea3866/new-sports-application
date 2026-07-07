package com.sportsapp.infrastructure.community.mysql

import com.sportsapp.domain.community.entity.CommunityBooking
import com.sportsapp.domain.community.repository.CommunityBookingRepository
import org.springframework.stereotype.Component

@Component
class CommunityBookingRepositoryImpl(
    private val communityBookingJpaRepository: CommunityBookingJpaRepository,
) : CommunityBookingRepository {

    override fun save(booking: CommunityBooking): CommunityBooking = communityBookingJpaRepository.save(booking)

    override fun findBy(communityId: Long, slotId: Long): CommunityBooking? =
        communityBookingJpaRepository.findByCommunityIdAndSlotIdAndDeletedAtIsNull(communityId, slotId)

    override fun findAllBy(communityId: Long): List<CommunityBooking> =
        communityBookingJpaRepository.findAllByCommunityIdAndDeletedAtIsNull(communityId)
}
