package com.sportsapp.presentation.facility.controller

import com.sportsapp.application.facility.dto.RemoveHolidayCommand
import com.sportsapp.application.facility.usecase.AddHolidayUseCase
import com.sportsapp.application.facility.usecase.RegisterOperatingHoursUseCase
import com.sportsapp.application.facility.usecase.RemoveHolidayUseCase
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.facility.dto.request.HolidayRequest
import com.sportsapp.presentation.facility.dto.request.RegisterOperatingHoursRequest
import com.sportsapp.presentation.facility.dto.response.FacilityResponse
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/facilities/{facilityId}")
@Profile("!test-jpa")
@PreAuthorize("hasRole('FACILITY_OWNER')")
class FacilityScheduleApiController(
    private val registerOperatingHoursUseCase: RegisterOperatingHoursUseCase,
    private val addHolidayUseCase: AddHolidayUseCase,
    private val removeHolidayUseCase: RemoveHolidayUseCase,
) {
    @PutMapping("/operating-hours")
    fun registerOperatingHours(
        @PathVariable facilityId: String,
        @RequestBody request: RegisterOperatingHoursRequest,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<FacilityResponse> {
        val facility = registerOperatingHoursUseCase.execute(request.toCommand(facilityId, principal.id))
        return ResponseEntity.ok(FacilityResponse.of(facility))
    }

    @PostMapping("/holidays")
    fun addHoliday(
        @PathVariable facilityId: String,
        @RequestBody request: HolidayRequest,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<FacilityResponse> {
        val facility = addHolidayUseCase.execute(request.toCommand(facilityId, principal.id))
        return ResponseEntity.ok(FacilityResponse.of(facility))
    }

    @DeleteMapping("/holidays")
    fun removeHoliday(
        @PathVariable facilityId: String,
        @RequestParam date: String,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<FacilityResponse> {
        val command = RemoveHolidayCommand(facilityId = facilityId, ownerUserId = principal.id, date = LocalDate.parse(date))
        val facility = removeHolidayUseCase.execute(command)
        return ResponseEntity.ok(FacilityResponse.of(facility))
    }
}
