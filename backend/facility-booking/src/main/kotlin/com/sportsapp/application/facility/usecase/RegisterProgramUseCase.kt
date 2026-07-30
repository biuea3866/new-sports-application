package com.sportsapp.application.facility.usecase

import com.sportsapp.application.facility.dto.ProgramResponse
import com.sportsapp.application.facility.dto.RegisterProgramCommand
import com.sportsapp.domain.facility.service.ProgramDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class RegisterProgramUseCase(
    private val programDomainService: ProgramDomainService,
) {
    @Transactional
    fun execute(command: RegisterProgramCommand): ProgramResponse {
        val program = programDomainService.register(
            facilityId = command.facilityId,
            ownerUserId = command.ownerUserId,
            name = command.name,
            description = command.description,
            price = command.price,
            capacity = command.capacity,
            durationMinutes = command.durationMinutes,
        )
        return ProgramResponse.of(program)
    }
}
