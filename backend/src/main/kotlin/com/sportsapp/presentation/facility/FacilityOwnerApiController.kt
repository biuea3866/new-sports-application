package com.sportsapp.presentation.facility

import com.sportsapp.application.facility.DeleteMyFacilityCommand
import com.sportsapp.application.facility.DeleteMyFacilityUseCase
import com.sportsapp.application.facility.FacilityResponse
import com.sportsapp.application.facility.GetMyFacilityUseCase
import com.sportsapp.application.facility.ListMyFacilitiesUseCase
import com.sportsapp.application.facility.RegisterMyFacilityUseCase
import com.sportsapp.application.facility.UpdateMyFacilityUseCase
import com.sportsapp.domain.user.UserPrincipal
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
        val response = registerMyFacilityUseCase.execute(request.toCommand(principal.id))
        return ResponseEntity.ok(response)
    }

    @GetMapping
    fun listFacilities(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<Page<FacilityResponse>> {
        val response = listMyFacilitiesUseCase.execute(principal.id, page, size)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{id}")
    fun getFacility(
        @PathVariable id: String,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<FacilityResponse> {
        val response = getMyFacilityUseCase.execute(id, principal.id)
        return ResponseEntity.ok(response)
    }

    @PatchMapping("/{id}")
    fun updateFacility(
        @PathVariable id: String,
        @RequestBody request: UpdateFacilityRequest,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<FacilityResponse> {
        val response = updateMyFacilityUseCase.execute(request.toCommand(id, principal.id))
        return ResponseEntity.ok(response)
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
