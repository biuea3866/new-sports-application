package com.sportsapp.infrastructure.recruitment.mysql

import com.sportsapp.domain.recruitment.entity.Recruitment
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface RecruitmentJpaRepository : JpaRepository<Recruitment, Long>, RecruitmentQueryDslRepository {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findForUpdateById(id: Long): Recruitment?
}
