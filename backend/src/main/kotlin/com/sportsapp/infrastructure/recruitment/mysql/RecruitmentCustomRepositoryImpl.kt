package com.sportsapp.infrastructure.recruitment.mysql

import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.recruitment.entity.QRecruitment.recruitment
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.entity.RecruitmentStatus
import com.sportsapp.domain.recruitment.repository.RecruitmentCustomRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class RecruitmentCustomRepositoryImpl : RecruitmentCustomRepository {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val queryFactory: JPAQueryFactory
        get() = JPAQueryFactory(entityManager)

    override fun searchOpen(keyword: String?, pageable: Pageable): Page<Recruitment> {
        val condition = buildCondition(keyword)
        val content = fetchContent(condition, pageable)
        val total = fetchCount(condition)
        return PageImpl(content, pageable, total)
    }

    private fun buildCondition(keyword: String?): BooleanBuilder {
        val builder = BooleanBuilder()
        builder.and(recruitment.status.eq(RecruitmentStatus.OPEN))
        builder.and(recruitment.deletedAt.isNull)
        keyword?.takeIf { it.isNotBlank() }?.let { builder.and(recruitment.title.containsIgnoreCase(it)) }
        return builder
    }

    private fun fetchContent(condition: BooleanBuilder, pageable: Pageable): List<Recruitment> =
        queryFactory.selectFrom(recruitment)
                    .where(condition)
                    .offset(pageable.offset)
                    .limit(pageable.pageSize.toLong())
                    .orderBy(recruitment.createdAt.desc())
                    .fetch()

    private fun fetchCount(condition: BooleanBuilder): Long =
        queryFactory.select(recruitment.count())
                    .from(recruitment)
                    .where(condition)
                    .fetchOne() ?: 0L
}
