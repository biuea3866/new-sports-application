package com.sportsapp.presentation.facility.controller

import com.sportsapp.application.facility.dto.DeleteMyFacilityCommand
import com.sportsapp.application.facility.usecase.DeleteMyFacilityUseCase
import com.sportsapp.application.facility.usecase.GetMyFacilityUseCase
import com.sportsapp.application.facility.usecase.ListMyFacilitiesUseCase
import com.sportsapp.application.facility.usecase.RegisterMyFacilityUseCase
import com.sportsapp.application.facility.usecase.UpdateMyFacilityUseCase
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.facility.dto.request.RegisterFacilityRequest
import com.sportsapp.presentation.facility.dto.request.UpdateFacilityRequest
import com.sportsapp.presentation.facility.dto.response.FacilityResponse
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/facility-owner/facilities")
@Profile("!test-jpa")
@PreAuthorize("hasRole('FACILITY_OWNER')")
class FacilityOwnerApiController(
    private val registerMyFacilityUseCase: RegisterMyFacilityUseCase,
    private val listMyFacilitiesUseCase: ListMyFacilitiesUseCase,
    private val getMyFacilityUseCase: GetMyFacilityUseCase,
    private val updateMyFacilityUseCase: UpdateMyFacilityUseCase,
    private val deleteMyFacilityUseCase: DeleteMyFacilityUseCase,
) {
    @PostMapping
    fun registerFacility(
        @RequestBody request: RegisterFacilityRequest,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<FacilityResponse> {
        val facility = registerMyFacilityUseCase.execute(request.toCommand(principal.id))
        return ResponseEntity.ok(FacilityResponse.of(facility))
    }

    @GetMapping
    fun listFacilities(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<Page<FacilityResponse>> {
        val facilities = listMyFacilitiesUseCase.execute(principal.id, page, size)
        return ResponseEntity.ok(facilities.map { FacilityResponse.of(it) })
    }

    @GetMapping("/{id}")
    fun getFacility(
        @PathVariable id: String,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<FacilityResponse> {
        val facility = getMyFacilityUseCase.execute(id, principal.id)
        return ResponseEntity.ok(FacilityResponse.of(facility))
    }

    @PatchMapping("/{id}")
    fun updateFacility(
        @PathVariable id: String,
        @RequestBody request: UpdateFacilityRequest,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<FacilityResponse> {
        val facility = updateMyFacilityUseCase.execute(request.toCommand(id, principal.id))
        return ResponseEntity.ok(FacilityResponse.of(facility))
    }

    @DeleteMapping("/{id}")
    fun deleteFacility(
        @PathVariable id: String,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<Void> {
        deleteMyFacilityUseCase.execute(DeleteMyFacilityCommand(facilityId = id, ownerUserId = principal.id))
        return ResponseEntity.noContent().build()
    }
}
