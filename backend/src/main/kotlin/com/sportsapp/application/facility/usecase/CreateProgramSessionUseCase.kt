package com.sportsapp.application.facility.usecase

import com.sportsapp.application.facility.dto.CreateProgramSessionCommand
import com.sportsapp.application.facility.dto.ProgramSessionResponse
import com.sportsapp.domain.booking.service.SlotDomainService
import com.sportsapp.domain.facility.service.ProgramDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * program 회차(programId를 가진 Slot) 생성 (BE-59, "선택" 오케스트레이션).
 *
 * facility(Program)·booking(Slot) 두 DomainService만 호출한다 — Repository/Gateway 직접 주입
 * 금지(ArchUnit `AggregateAndUseCaseRulesTest`)를 지키면서, 두 코어 도메인의 크로스 오케스트레이션은
 * application 레이어(도메인 슬라이스 규칙 범위 밖)에서 수행한다. 예약·동시성은 기존
 * [SlotDomainService.createSlot]/`BookingDomainService.requestBooking` 그대로 재사용(무변경).
 */
@Service
@Profile("!test-jpa")
class CreateProgramSessionUseCase(
    private val programDomainService: ProgramDomainService,
    private val slotDomainService: SlotDomainService,
) {
    @Transactional
    fun execute(command: CreateProgramSessionCommand): ProgramSessionResponse {
        val program = programDomainService.getOwnedProgram(command.requesterId, command.programId)
        val slot = slotDomainService.createSlot(
            ownerId = command.requesterId,
            facilityId = program.facilityId,
            date = command.date,
            timeRange = command.timeRange,
            capacity = program.capacity,
            programId = program.id,
        )
        return ProgramSessionResponse.of(slot)
    }
}
