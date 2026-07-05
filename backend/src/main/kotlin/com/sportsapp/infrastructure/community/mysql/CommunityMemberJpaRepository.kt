package com.sportsapp.infrastructure.community.mysql

import com.sportsapp.domain.community.entity.CommunityMember
import com.sportsapp.domain.community.vo.MembershipStatus
import org.springframework.data.jpa.repository.JpaRepository

interface CommunityMemberJpaRepository : JpaRepository<CommunityMember, Long> {
    fun findByCommunityIdAndUserIdAndDeletedAtIsNull(communityId: Long, userId: Long): CommunityMember?
    fun findByCommunityIdAndUserIdAndStatusAndDeletedAtIsNull(
        communityId: Long,
        userId: Long,
        status: MembershipStatus,
    ): CommunityMember?
    fun findAllByCommunityIdAndStatusAndDeletedAtIsNull(communityId: Long, status: MembershipStatus): List<CommunityMember>
    fun countByCommunityIdAndStatusAndDeletedAtIsNull(communityId: Long, status: MembershipStatus): Long
}
