package com.sportsapp.domain.recruitment.repository

import com.sportsapp.domain.recruitment.entity.Recruitment

interface RecruitmentRepository {
    fun save(recruitment: Recruitment): Recruitment
    fun findById(id: Long): Recruitment?
    fun findForUpdateById(id: Long): Recruitment?
    fun findAll(communityId: Long?): List<Recruitment>
}
