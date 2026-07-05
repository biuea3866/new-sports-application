package com.sportsapp.domain.community.service

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.community.entity.Community
import com.sportsapp.domain.community.entity.CommunityMember
import com.sportsapp.domain.community.exception.NotCommunityMemberException
import com.sportsapp.domain.community.repository.CommunityMemberRepository
import com.sportsapp.domain.community.repository.CommunityRepository
import com.sportsapp.domain.community.vo.CommunityVisibility
import com.sportsapp.domain.community.vo.MembershipStatus
import com.sportsapp.domain.community.vo.SportCategory
import org.springframework.stereotype.Service

/**
 * 커뮤니티·멤버십 오케스트레이션 (TDD Detail Design "CommunityDomainService").
 *
 * TDD가 명령(create/join/approve/kick/leave/transfer)과 조회(getCommunity/findPublicCommunities/
 * findMembers/findMyCommunities/countActiveMembers/requireActiveMember)를 한 클래스로 확정했다 —
 * 커뮤니티·멤버십이라는 단일 애그리거트 경계의 책임이라 detekt 기본 임계값(11)을 넘긴다.
 */
@Suppress("TooManyFunctions")
@Service
class CommunityDomainService(
    private val communityRepository: CommunityRepository,
    private val communityMemberRepository: CommunityMemberRepository,
    private val domainEventPublisher: DomainEventPublisher,
) {

    /**
     * 커뮤니티 개설 — 개설자를 방장 멤버십으로 함께 생성한다 (FR-1).
     *
     * 이벤트는 save() 로 id 가 확정된 **이후** [Community.registerCreatedEvent]/
     * [CommunityMember.registerJoinedEvent] 로 적재한다 (리뷰 p2-② — id=0 고정 방지).
     */
    fun create(
        name: String,
        description: String?,
        visibility: CommunityVisibility,
        sportCategory: SportCategory,
        hostUserId: Long,
    ): Community {
        val community = Community.create(
            name = name,
            description = description,
            visibility = visibility,
            sportCategory = sportCategory,
            hostUserId = hostUserId,
        )
        val saved = communityRepository.save(community)
        saved.registerCreatedEvent()
        val hostMember = communityMemberRepository.save(CommunityMember.createHost(saved.id, hostUserId))
        hostMember.registerJoinedEvent()
        domainEventPublisher.publishAll(saved.pullDomainEvents() + hostMember.pullDomainEvents())
        return saved
    }

    /**
     * 커뮤니티 가입 — 공개는 즉시 ACTIVE, 비공개는 PENDING_APPROVAL (FR-2).
     *
     * 기존 멤버십 레코드가 있으면(과거 탈퇴·강퇴) [CommunityMember.rejoin]으로 재활성화한다 —
     * 항상 새 row를 INSERT하면 `community_members` UNIQUE(community_id, user_id, deleted_at)
     * 제약 위반(500)이 난다 (리뷰 p2-①). 이미 ACTIVE/PENDING_APPROVAL이면 rejoin이
     * [com.sportsapp.domain.community.exception.AlreadyCommunityMemberException]으로 거부한다.
     */
    fun join(communityId: Long, userId: Long): CommunityMember {
        val community = getCommunityById(communityId)
        val existing = communityMemberRepository.findBy(communityId, userId)
        val member = if (existing != null) {
            existing.rejoin(community.isPublic())
            communityMemberRepository.save(existing)
        } else {
            val created = communityMemberRepository.save(CommunityMember.join(communityId, userId, community.isPublic()))
            if (created.currentStatus == MembershipStatus.ACTIVE) created.registerJoinedEvent()
            created
        }
        domainEventPublisher.publishAll(member.pullDomainEvents())
        return member
    }

    /** 방장 승인 — PENDING_APPROVAL 대상을 ACTIVE로 전이한다 (FR-2). */
    fun approve(communityId: Long, requesterId: Long, targetUserId: Long): CommunityMember {
        val community = getCommunityById(communityId)
        community.requireHost(requesterId)
        val member = findMemberOrThrow(communityId, targetUserId)
        member.approve()
        val saved = communityMemberRepository.save(member)
        domainEventPublisher.publishAll(saved.pullDomainEvents())
        return saved
    }

    /** 강퇴 — 방장만 수행 가능하고, 대상이 방장 본인이면 Entity가 거부한다 (FR-3/5). */
    fun kick(communityId: Long, requesterId: Long, targetUserId: Long) {
        val community = getCommunityById(communityId)
        community.requireHost(requesterId)
        val member = findMemberOrThrow(communityId, targetUserId)
        member.kick()
        val saved = communityMemberRepository.save(member)
        domainEventPublisher.publishAll(saved.pullDomainEvents())
    }

    /** 탈퇴 — 방장은 위임 전에는 Entity가 거부한다 (FR-3). */
    fun leave(communityId: Long, userId: Long) {
        val member = findMemberOrThrow(communityId, userId)
        member.leave()
        val saved = communityMemberRepository.save(member)
        domainEventPublisher.publishAll(saved.pullDomainEvents())
    }

    /**
     * 방장 권한 위임 — 기존 방장은 MEMBER로, 신규 사용자는 HOST로 전이한다 (FR-3).
     * 신규 방장 대상은 ACTIVE 멤버여야 한다 — [requireActiveMember]로 검증한다(리뷰 p3,
     * PENDING_APPROVAL/LEFT/KICKED 상태에게 방장 권한이 넘어가는 것을 방지).
     */
    fun transfer(communityId: Long, requesterId: Long, newHostUserId: Long) {
        val community = getCommunityById(communityId)
        community.requireHost(requesterId)
        requireActiveMember(communityId, newHostUserId)
        val oldHostMember = findMemberOrThrow(communityId, requesterId)
        val newHostMember = findMemberOrThrow(communityId, newHostUserId)
        community.transferHostTo(newHostUserId)
        oldHostMember.demoteToMember()
        newHostMember.promoteToHost()
        communityRepository.save(community)
        communityMemberRepository.save(oldHostMember)
        communityMemberRepository.save(newHostMember)
    }

    /** 커뮤니티 상세 — 공개는 통과, 비공개는 [requireActiveMember] 인가 가드 (FR-13 ②). */
    fun getCommunity(communityId: Long, requesterId: Long): Community {
        val community = getCommunityById(communityId)
        if (!community.isPublic()) requireActiveMember(communityId, requesterId)
        return community
    }

    /** 공개 커뮤니티 키워드 검색 — 인가 불요 (FR-1). */
    fun findPublicCommunities(keyword: String?): List<Community> =
        communityRepository.findPublicByKeyword(keyword)

    /** 커뮤니티 멤버 목록 — 요청자가 ACTIVE 멤버인지 서버가 강제한다 (FR-13 ②). */
    fun findMembers(communityId: Long, requesterId: Long): List<CommunityMember> {
        requireActiveMember(communityId, requesterId)
        return communityMemberRepository.findActiveByCommunityId(communityId)
    }

    /** 내가 ACTIVE 멤버인 커뮤니티 목록 (FR-3). */
    fun findMyCommunities(userId: Long): List<Community> =
        communityRepository.findByMemberUserId(userId)

    /**
     * 커뮤니티의 ACTIVE 멤버 수 — `CommunityResponse.memberCount` 집계용. 인가 불요(공개 목록에도 노출).
     * COUNT 쿼리로 집계한다(리뷰 p3 — 목록 전체를 로드하지 않는다).
     */
    fun countActiveMembers(communityId: Long): Int =
        communityMemberRepository.countActiveByCommunityId(communityId).toInt()

    /**
     * 요청자가 해당 커뮤니티의 ACTIVE 멤버인지 서버에서 강제한다 (FR-13 ②).
     *
     * 컨텍스트 방(contextType=COMMUNITY) 참여자일 뿐인 게스트는 `community_members`에
     * ACTIVE 레코드가 없어 여기서 거부된다 — contextId를 communityId로 재사용해도 우회할 수 없다.
     */
    fun requireActiveMember(communityId: Long, requesterId: Long) {
        val member = communityMemberRepository.findActiveBy(communityId, requesterId)
        if (member == null || member.currentStatus != MembershipStatus.ACTIVE) {
            throw NotCommunityMemberException(communityId, requesterId)
        }
    }

    private fun getCommunityById(communityId: Long): Community =
        communityRepository.findById(communityId) ?: throw ResourceNotFoundException("Community", communityId)

    private fun findMemberOrThrow(communityId: Long, userId: Long): CommunityMember =
        communityMemberRepository.findBy(communityId, userId) ?: throw ResourceNotFoundException("CommunityMember", userId)
}
