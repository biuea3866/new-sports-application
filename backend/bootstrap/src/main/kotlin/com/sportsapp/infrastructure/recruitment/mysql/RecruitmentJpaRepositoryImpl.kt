package com.sportsapp.infrastructure.recruitment.mysql

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.recruitment.entity.QRecruitment.recruitment
import com.sportsapp.domain.recruitment.entity.Recruitment
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext

class RecruitmentJpaRepositoryImpl : RecruitmentQueryDslRepository {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val queryFactory: JPAQueryFactory
        get() = JPAQueryFactory(entityManager)

    override fun findAllBy(communityId: Long?): List<Recruitment> =
        queryFactory.selectFrom(recruitment)
                    .where(communityId?.let { recruitment.communityId.eq(it) })
                    .orderBy(recruitment.createdAt.desc())
                    .fetch()
}
