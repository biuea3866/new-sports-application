package com.sportsapp.infrastructure.community.mysql

import com.sportsapp.domain.community.entity.CommunityMember
import com.sportsapp.domain.community.repository.CommunityMemberRepository
import com.sportsapp.domain.community.vo.MembershipStatus
import org.springframework.stereotype.Component

@Component
class CommunityMemberRepositoryImpl(
    private val communityMemberJpaRepository: CommunityMemberJpaRepository,
) : CommunityMemberRepository {

    override fun save(member: CommunityMember): CommunityMember = communityMemberJpaRepository.save(member)

    override fun findBy(communityId: Long, userId: Long): CommunityMember? =
        communityMemberJpaRepository.findByCommunityIdAndUserIdAndDeletedAtIsNull(communityId, userId)

    override fun findActiveBy(communityId: Long, userId: Long): CommunityMember? =
        communityMemberJpaRepository.findByCommunityIdAndUserIdAndStatusAndDeletedAtIsNull(
            communityId,
            userId,
            MembershipStatus.ACTIVE,
        )

    override fun findActiveByCommunityId(communityId: Long): List<CommunityMember> =
        communityMemberJpaRepository.findAllByCommunityIdAndStatusAndDeletedAtIsNull(communityId, MembershipStatus.ACTIVE)
}
