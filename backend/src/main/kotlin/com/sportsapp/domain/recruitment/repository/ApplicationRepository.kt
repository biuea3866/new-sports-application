package com.sportsapp.domain.recruitment.repository

import com.sportsapp.domain.recruitment.entity.Application

interface ApplicationRepository {
    fun save(application: Application): Application
    fun findById(id: Long): Application?
    fun countActiveByRecruitmentId(recruitmentId: Long): Int
    fun findByRecruitmentId(recruitmentId: Long): List<Application>
    fun findConfirmedByRecruitmentId(recruitmentId: Long): List<Application>
}
