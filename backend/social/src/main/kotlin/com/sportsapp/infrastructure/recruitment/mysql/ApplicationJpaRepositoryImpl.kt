package com.sportsapp.infrastructure.recruitment.mysql

import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.recruitment.dto.ApplicationExpiryCandidate
import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.entity.QApplication.application
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.time.ZonedDateTime

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

    /**
     * W1-11d 만료 스위퍼가 소비 — PENDING 상태·삭제되지 않았으며 createdAt이 before보다
     * 이르고 id가 afterId보다 큰 신청을 id 오름차순으로 최대 limit건 조회한다(청크 조회).
     * afterId 커서로 한 주기 내 이미 훑은(만료 금지 가드로 건너뛴 건 포함) 구간을 다시
     * 스캔하지 않는다 — 커서 없이는 결제 진행 중이라 건너뛴 신청이 다음 청크에서 계속
     * 재조회되어 스위퍼가 진행하지 못하는 head-of-line blocking이 생긴다.
     */
    override fun findPendingCreatedBefore(before: ZonedDateTime, afterId: Long, limit: Int): List<ApplicationExpiryCandidate> =
        queryFactory.select(Projections.constructor(ApplicationExpiryCandidate::class.java, application.id, application.createdAt))
                    .from(application)
                    .where(
                        application.status.eq(ApplicationStatus.PENDING),
                        application.createdAt.lt(before),
                        application.id.gt(afterId),
                        application.deletedAt.isNull,
                    )
                    .orderBy(application.id.asc())
                    .limit(limit.toLong())
                    .fetch()

    /**
     * W1-11d 만료 스위퍼 CAS 쓰기 — 현재 상태가 PENDING일 때만 CANCELLED로 원자적 전이한다
     * (조건부 UPDATE, WHERE status = PENDING). 영향 행 수(affected rows > 0)로 성공 여부를
     * 반환한다.
     */
    override fun tryExpire(applicationId: Long): Boolean {
        val affectedRows = queryFactory.update(application)
                                       .set(application.status, ApplicationStatus.CANCELLED)
                                       .set(application.updatedAt, ZonedDateTime.now())
                                       .where(
                                           application.id.eq(applicationId),
                                           application.status.eq(ApplicationStatus.PENDING),
                                       )
                                       .execute()
        return affectedRows > 0
    }
}
