package com.sportsapp.infrastructure.recruitment.mysql

import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.repository.ApplicationRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class ApplicationRepositoryImpl(
    private val applicationJpaRepository: ApplicationJpaRepository,
) : ApplicationRepository {

    override fun save(application: Application): Application =
        applicationJpaRepository.save(application)

    override fun findById(id: Long): Application? =
        applicationJpaRepository.findByIdOrNull(id)

    override fun countActiveByRecruitmentId(recruitmentId: Long): Int =
        applicationJpaRepository.countActiveByRecruitmentId(recruitmentId)

    override fun findByRecruitmentId(recruitmentId: Long): List<Application> =
        applicationJpaRepository.findAllByRecruitmentId(recruitmentId)

    override fun findConfirmedByRecruitmentId(recruitmentId: Long): List<Application> =
        applicationJpaRepository.findAllByRecruitmentIdAndStatus(recruitmentId, ApplicationStatus.CONFIRMED)

    override fun findByApplicantUserId(applicantUserId: Long): List<Application> =
        applicationJpaRepository.findByApplicantUserId(applicantUserId)
}
