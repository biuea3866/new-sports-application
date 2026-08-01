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

    /**
     * 소프트 삭제된 모집은 목록에서 제외한다 — JpaAuditingBase 정책("모든 Repository 조회는
     * 기본으로 WHERE deleted_at IS NULL"). 이 필터가 빠져 삭제된 모집이 목록에 노출됐다.
     */
    override fun findAllBy(communityId: Long?): List<Recruitment> =
        queryFactory.selectFrom(recruitment)
                    .where(
                        recruitment.deletedAt.isNull,
                        communityId?.let { recruitment.communityId.eq(it) },
                    )
                    .orderBy(recruitment.createdAt.desc())
                    .fetch()
}
