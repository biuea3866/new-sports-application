package com.sportsapp.infrastructure.community.mysql

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.community.entity.Community
import com.sportsapp.domain.community.entity.QCommunity.community
import com.sportsapp.domain.community.entity.QCommunityMember.communityMember
import com.sportsapp.domain.community.repository.CommunityCustomRepository
import com.sportsapp.domain.community.vo.CommunityVisibility
import com.sportsapp.domain.community.vo.MembershipStatus
import org.springframework.stereotype.Component

@Component
class CommunityCustomRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : CommunityCustomRepository {

    override fun findPublicByKeyword(keyword: String?): List<Community> {
        val query = queryFactory.selectFrom(community)
            .where(
                community.visibility.eq(CommunityVisibility.PUBLIC),
                community.deletedAt.isNull,
            )
        if (!keyword.isNullOrBlank()) {
            query.where(community.name.containsIgnoreCase(keyword))
        }
        return query.fetch()
    }

    override fun findByMemberUserId(userId: Long): List<Community> {
        return queryFactory.selectFrom(community)
            .join(communityMember).on(
                communityMember.communityId.eq(community.id),
                communityMember.userId.eq(userId),
                communityMember.status.eq(MembershipStatus.ACTIVE),
                communityMember.deletedAt.isNull,
            )
            .where(community.deletedAt.isNull)
            .fetch()
    }
}
