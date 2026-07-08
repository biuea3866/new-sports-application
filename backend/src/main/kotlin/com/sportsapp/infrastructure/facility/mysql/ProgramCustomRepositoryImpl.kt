package com.sportsapp.infrastructure.facility.mysql

import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.facility.entity.Program
import com.sportsapp.domain.facility.entity.QProgram.program
import com.sportsapp.domain.facility.repository.ProgramCustomRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class ProgramCustomRepositoryImpl : ProgramCustomRepository {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val queryFactory: JPAQueryFactory
        get() = JPAQueryFactory(entityManager)

    override fun searchForCatalog(keyword: String?, pageable: Pageable): Page<Program> {
        val condition = buildCondition(keyword)
        val content = fetchContent(condition, pageable)
        val total = fetchCount(condition)
        return PageImpl(content, pageable, total)
    }

    private fun buildCondition(keyword: String?): BooleanBuilder {
        val builder = BooleanBuilder()
        builder.and(program.deletedAt.isNull)
        keyword?.takeIf { it.isNotBlank() }?.let { builder.and(program.name.containsIgnoreCase(it)) }
        return builder
    }

    private fun fetchContent(condition: BooleanBuilder, pageable: Pageable): List<Program> =
        queryFactory.selectFrom(program)
                    .where(condition)
                    .offset(pageable.offset)
                    .limit(pageable.pageSize.toLong())
                    .orderBy(program.createdAt.desc())
                    .fetch()

    private fun fetchCount(condition: BooleanBuilder): Long =
        queryFactory.select(program.count())
                    .from(program)
                    .where(condition)
                    .fetchOne() ?: 0L
}
