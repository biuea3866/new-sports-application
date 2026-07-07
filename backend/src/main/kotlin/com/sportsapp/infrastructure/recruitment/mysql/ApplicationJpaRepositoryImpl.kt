package com.sportsapp.infrastructure.recruitment.mysql

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.entity.QApplication.application
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext

private val ACTIVE_STATUSES = listOf(ApplicationStatus.PENDING, ApplicationStatus.CONFIRMED)

class ApplicationJpaRepositoryImpl : ApplicationQueryDslRepository {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val queryFactory: JPAQueryFactory
        get() = JPAQueryFactory(entityManager)

    override fun countActiveByRecruitmentId(recruitmentId: Long): Int =
        queryFactory.select(application.count())
                    .from(application)
                    .where(
                        application.recruitmentId.eq(recruitmentId),
                        application.status.`in`(ACTIVE_STATUSES),
                    )
                    .fetchOne()?.toInt() ?: 0

    override fun findByApplicantUserId(applicantUserId: Long): List<Application> =
        queryFactory.selectFrom(application)
                    .where(application.applicantUserId.eq(applicantUserId))
                    .orderBy(application.createdAt.desc())
                    .fetch()
}
