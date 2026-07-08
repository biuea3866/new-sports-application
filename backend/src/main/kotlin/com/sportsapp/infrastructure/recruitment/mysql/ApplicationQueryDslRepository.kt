package com.sportsapp.infrastructure.recruitment.mysql

import com.sportsapp.domain.recruitment.entity.Application

interface ApplicationQueryDslRepository {
    fun countActiveByRecruitmentId(recruitmentId: Long): Int
    fun findByApplicantUserId(applicantUserId: Long): List<Application>
}
