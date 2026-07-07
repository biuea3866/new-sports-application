package com.sportsapp.infrastructure.recruitment.mysql

import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import org.springframework.data.jpa.repository.JpaRepository

interface ApplicationJpaRepository : JpaRepository<Application, Long>, ApplicationQueryDslRepository {
    fun findAllByRecruitmentId(recruitmentId: Long): List<Application>
    fun findAllByRecruitmentIdAndStatus(recruitmentId: Long, status: ApplicationStatus): List<Application>
}
