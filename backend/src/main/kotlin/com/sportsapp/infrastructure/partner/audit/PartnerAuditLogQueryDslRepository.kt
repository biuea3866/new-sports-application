package com.sportsapp.infrastructure.partner.audit

import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.partner.audit.PartnerAuditLog
import com.sportsapp.domain.partner.audit.PartnerAuditLogCustomRepository
import com.sportsapp.infrastructure.partner.audit.QPartnerAuditLogJpaEntity.partnerAuditLogJpaEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime

/**
 * A1 인덱스(`idx_partner_audit_log_partner_id_called_at`) ESR 순서에 맞춰
 * partnerId 등가 → calledAt 범위·정렬 순으로 조회한다.
 */
@Repository
class PartnerAuditLogQueryDslRepository(
    private val queryFactory: JPAQueryFactory,
) : PartnerAuditLogCustomRepository {

    override fun findBy(
        partnerId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
        pageable: Pageable,
    ): Page<PartnerAuditLog> {
        val predicate = buildPredicate(partnerId, from, to)
        val content = fetchContent(predicate, pageable)
        val total = fetchCount(predicate)
        return PageImpl(content, pageable, total)
    }

    private fun buildPredicate(partnerId: Long, from: ZonedDateTime, to: ZonedDateTime): BooleanBuilder {
        val predicate = BooleanBuilder()
        predicate.and(partnerAuditLogJpaEntity.partnerId.eq(partnerId))
        predicate.and(partnerAuditLogJpaEntity.calledAt.between(from, to))
        return predicate
    }

    private fun fetchContent(predicate: BooleanBuilder, pageable: Pageable): List<PartnerAuditLog> =
        queryFactory.selectFrom(partnerAuditLogJpaEntity)
                    .where(predicate)
                    .orderBy(partnerAuditLogJpaEntity.calledAt.desc())
                    .offset(pageable.offset)
                    .limit(pageable.pageSize.toLong())
                    .fetch()
                    .map { it.toDomain() }

    private fun fetchCount(predicate: BooleanBuilder): Long =
        queryFactory.select(partnerAuditLogJpaEntity.count())
                    .from(partnerAuditLogJpaEntity)
                    .where(predicate)
                    .fetchOne() ?: 0L
}
