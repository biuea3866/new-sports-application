package com.sportsapp.domain.community.service

import com.sportsapp.domain.community.dto.CommunityBookingResult
import com.sportsapp.domain.community.entity.CommunityBooking
import com.sportsapp.domain.community.gateway.SlotInfoGateway
import com.sportsapp.domain.community.repository.CommunityBookingRepository
import org.springframework.stereotype.Service

/**
 * 소모임 예약 연결·조회 오케스트레이션 (TDD B3 Detail Design "CommunityBookingDomainService").
 *
 * 인가는 [CommunityDomainService]에 위임한다(같은 도메인 내 — Community.kt/CommunityDomainService.kt는
 * BE-23이 이미 확장해 Single Writer 원칙상 본 티켓에서 수정하지 않는다). 슬롯 표시정보는
 * [SlotInfoGateway] ACL로만 조회한다(R1 — booking import 금지).
 */
@Service
class CommunityBookingDomainService(
    private val communityBookingRepository: CommunityBookingRepository,
    private val communityDomainService: CommunityDomainService,
    private val slotInfoGateway: SlotInfoGateway,
) {

    /** 방장이 slotId를 모임 활동으로 연결한다. 동일 slotId 재연결은 멱등하게 기존 링크를 반환한다. */
    fun link(communityId: Long, hostUserId: Long, slotId: Long): CommunityBooking {
        val community = communityDomainService.getCommunity(communityId, hostUserId)
        community.requireHost(hostUserId)
        val existing = communityBookingRepository.findBy(communityId, slotId)
        if (existing != null) return existing
        return communityBookingRepository.save(CommunityBooking.create(communityId, slotId, hostUserId))
    }

    /** 모임 멤버가 연결된 예약 목록을 조회한다. 표시용 시설·일시·정원은 SlotInfoGateway로 조합한다. */
    fun findLinked(communityId: Long, requesterId: Long): List<CommunityBookingResult> {
        communityDomainService.requireActiveMember(communityId, requesterId)
        return communityBookingRepository.findAllBy(communityId)
            .map { booking -> CommunityBookingResult.of(booking, slotInfoGateway.findBy(booking.slotId)) }
    }
}
