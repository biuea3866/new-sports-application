package com.sportsapp.domain.facility.service

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.facility.entity.Program
import com.sportsapp.domain.facility.exception.FacilityNotFoundException
import com.sportsapp.domain.facility.repository.FacilityRepository
import com.sportsapp.domain.facility.repository.ProgramCustomRepository
import com.sportsapp.domain.facility.repository.ProgramRepository
import java.math.BigDecimal
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

/**
 * 시설상품(program) 등록·조회 (BE-59, TDD Detail Design "ProgramDomainService").
 *
 * 소유권 검증은 booking 도메인의 `FacilityOwnershipGateway`를 재사용하지 않고, 같은 facility
 * 컨텍스트의 [FacilityRepository]+[com.sportsapp.domain.facility.entity.Facility.requireOwnedBy]를
 * 직접 사용한다 — domain 컨텍스트 교차 참조 금지(ArchUnit `AggregateAndUseCaseRulesTest` "도메인
 * 컨텍스트는 서로 의존하지 않는다") 위반을 피하면서 동일한 검증 효과(존재 404·소유권 403)를 낸다.
 * [FacilityOwnerDomainService.getOwnedFacility]와 동일한 패턴이다.
 */
@Service
@Profile("!test-jpa")
class ProgramDomainService(
    private val programRepository: ProgramRepository,
    private val facilityRepository: FacilityRepository,
    private val programCustomRepository: ProgramCustomRepository,
) {
    fun register(
        facilityId: String,
        ownerUserId: Long,
        name: String,
        description: String?,
        price: BigDecimal,
        capacity: Int,
        durationMinutes: Int,
    ): Program {
        val facility = facilityRepository.findById(facilityId) ?: throw FacilityNotFoundException(facilityId)
        facility.requireOwnedBy(ownerUserId)
        val program = Program.create(
            facilityId = facilityId,
            ownerUserId = ownerUserId,
            name = name,
            description = description,
            price = price,
            capacity = capacity,
            durationMinutes = durationMinutes,
        )
        return programRepository.save(program)
    }

    fun findByFacility(facilityId: String): List<Program> =
        programRepository.findByFacilityId(facilityId)

    fun getOwnedProgram(requesterId: Long, programId: Long): Program {
        val program = programRepository.findById(programId) ?: throw ResourceNotFoundException("Program", programId)
        program.requireOwnedBy(requesterId)
        return program
    }

    fun searchForCatalog(keyword: String?, pageable: Pageable): Page<Program> =
        programCustomRepository.searchForCatalog(keyword, pageable)
}
