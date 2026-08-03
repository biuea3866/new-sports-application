package com.sportsapp.infrastructure.recruitment.mysql

import com.sportsapp.domain.recruitment.entity.Recruitment
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

/**
 * 조회 파생 쿼리는 모두 `AndDeletedAtIsNull`을 붙인다 — JpaAuditingBase 정책
 * ("모든 Repository 조회는 기본으로 WHERE deleted_at IS NULL", post 도메인과 동일 패턴).
 */
interface RecruitmentJpaRepository : JpaRepository<Recruitment, Long>, RecruitmentQueryDslRepository {
    fun findByIdAndDeletedAtIsNull(id: Long): Recruitment?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findForUpdateByIdAndDeletedAtIsNull(id: Long): Recruitment?
}
