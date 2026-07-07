package com.sportsapp.application.facility.usecase

import com.sportsapp.application.facility.dto.ProgramResponse
import com.sportsapp.domain.facility.service.ProgramDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class ListProgramsUseCase(
    private val programDomainService: ProgramDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(facilityId: String): List<ProgramResponse> =
        programDomainService.findByFacility(facilityId).map { ProgramResponse.of(it) }
}
