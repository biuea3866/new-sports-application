package com.sportsapp.presentation.facility.controller

import com.sportsapp.application.facility.dto.ProgramResponse
import com.sportsapp.application.facility.usecase.ListProgramsUseCase
import com.sportsapp.application.facility.usecase.RegisterProgramUseCase
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.facility.dto.request.RegisterProgramRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private const val PROGRAM_ENABLED_PROPERTY = "facility.program.enabled"

/**
 * 시설상품(program) REST 계약 (BE-59, TDD "REST API 계약"). `facility.program.enabled=false`면
 * 빈 자체가 등록되지 않아 programs 하위 전체 경로가 404(즉시 롤백 지점, Release Scenario).
 */
@RestController
@RequestMapping("/facilities/{facilityId}/programs")
@Profile("!test-jpa")
@ConditionalOnProperty(name = [PROGRAM_ENABLED_PROPERTY], havingValue = "true", matchIfMissing = false)
class ProgramApiController(
    private val registerProgramUseCase: RegisterProgramUseCase,
    private val listProgramsUseCase: ListProgramsUseCase,
) {

    @PostMapping
    fun registerProgram(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable facilityId: String,
        @RequestBody request: RegisterProgramRequest,
    ): ResponseEntity<ProgramResponse> {
        val response = registerProgramUseCase.execute(request.toCommand(facilityId, principal.id))
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping
    fun listPrograms(@PathVariable facilityId: String): ResponseEntity<List<ProgramResponse>> =
        ResponseEntity.ok(listProgramsUseCase.execute(facilityId))
}
