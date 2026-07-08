package com.sportsapp.infrastructure.community.mysql

import com.sportsapp.domain.community.entity.Community
import com.sportsapp.domain.community.repository.CommunityCustomRepository
import com.sportsapp.domain.community.repository.CommunityRepository
import org.springframework.stereotype.Component

@Component
class CommunityRepositoryImpl(
    private val communityJpaRepository: CommunityJpaRepository,
    private val communityCustomRepository: CommunityCustomRepository,
) : CommunityRepository {

    override fun save(community: Community): Community = communityJpaRepository.save(community)

    override fun findById(id: Long): Community? = communityJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findPublicByKeyword(keyword: String?): List<Community> =
        communityCustomRepository.findPublicByKeyword(keyword)

    override fun findByMemberUserId(userId: Long): List<Community> =
        communityCustomRepository.findByMemberUserId(userId)
}
