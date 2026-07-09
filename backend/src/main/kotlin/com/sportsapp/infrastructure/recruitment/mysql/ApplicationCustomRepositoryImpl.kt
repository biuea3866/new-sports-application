package com.sportsapp.infrastructure.recruitment.mysql

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.recruitment.dto.ApplicationWithRecruitmentTitle
import com.sportsapp.domain.recruitment.entity.QApplication.application
import com.sportsapp.domain.recruitment.entity.QRecruitment.recruitment
import com.sportsapp.domain.recruitment.repository.ApplicationCustomRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

@Repository
class ApplicationCustomRepositoryImpl : ApplicationCustomRepository {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val queryFactory: JPAQueryFactory
        get() = JPAQueryFactory(entityManager)

    override fun findBy(applicantUserId: Long): List<ApplicationWithRecruitmentTitle> =
        queryFactory
            .select(application.id, application.status, recruitment.title, recruitment.deletedAt)
            .from(application)
            .leftJoin(recruitment).on(recruitment.id.eq(application.recruitmentId))
            .where(application.applicantUserId.eq(applicantUserId))
            .orderBy(application.createdAt.desc())
            .fetch()
            .map { tuple ->
                val recruitmentTitle = tuple.get(recruitment.title)
                val recruitmentDeletedAt = tuple.get(recruitment.deletedAt)
                ApplicationWithRecruitmentTitle(
                    applicationId = requireNotNull(tuple.get(application.id)) { "application.id must not be null" },
                    status = requireNotNull(tuple.get(application.status)) { "application.status must not be null" },
                    recruitmentTitle = if (recruitmentTitle == null || recruitmentDeletedAt != null) "" else recruitmentTitle,
                )
            }
}
