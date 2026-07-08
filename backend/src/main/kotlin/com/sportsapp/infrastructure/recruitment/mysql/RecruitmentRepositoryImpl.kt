package com.sportsapp.infrastructure.recruitment.mysql

import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.repository.RecruitmentRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class RecruitmentRepositoryImpl(
    private val recruitmentJpaRepository: RecruitmentJpaRepository,
) : RecruitmentRepository {

    override fun save(recruitment: Recruitment): Recruitment =
        recruitmentJpaRepository.save(recruitment)

    override fun findById(id: Long): Recruitment? =
        recruitmentJpaRepository.findByIdOrNull(id)

    override fun findForUpdateById(id: Long): Recruitment? =
        recruitmentJpaRepository.findForUpdateById(id)

    override fun findAll(communityId: Long?): List<Recruitment> =
        recruitmentJpaRepository.findAllBy(communityId)
}
