package com.sportsapp.infrastructure.facility.mysql

import com.sportsapp.domain.facility.entity.Program
import com.sportsapp.domain.facility.repository.ProgramRepository
import org.springframework.stereotype.Repository

@Repository
class ProgramRepositoryImpl(
    private val programJpaRepository: ProgramJpaRepository,
) : ProgramRepository {

    override fun save(program: Program): Program =
        programJpaRepository.save(program)

    override fun findById(id: Long): Program? =
        programJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findByFacilityId(facilityId: String): List<Program> =
        programJpaRepository.findByFacilityIdAndDeletedAtIsNull(facilityId)
}
