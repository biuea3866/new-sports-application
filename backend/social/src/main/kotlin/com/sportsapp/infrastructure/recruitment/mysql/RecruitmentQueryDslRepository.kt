package com.sportsapp.infrastructure.recruitment.mysql

import com.sportsapp.domain.recruitment.entity.Recruitment

interface RecruitmentQueryDslRepository {
    fun findAllBy(communityId: Long?): List<Recruitment>
}
