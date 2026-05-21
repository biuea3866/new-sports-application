package com.sportsapp.presentation.facility

import com.sportsapp.application.facility.DeleteMyFacilityCommand
import com.sportsapp.application.facility.DeleteMyFacilityUseCase
import com.sportsapp.application.facility.GetMyFacilityUseCase
import com.sportsapp.application.facility.ListMyFacilitiesUseCase
import com.sportsapp.application.facility.MyFacilityResponse
import com.sportsapp.application.facility.RegisterMyFacilityUseCase
import com.sportsapp.application.facility.UpdateMyFacilityCommand
import com.sportsapp.application.facility.UpdateMyFacilityUseCase
import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.user.UserPrincipal
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
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
import org.springframework.web.util.UriComponentsBuilder

@RestController
@RequestMapping("/api/b2b/facilities")
@Profile("!test-jpa")
class B2bFacilityApiController(
    private val registerMyFacilityUseCase: RegisterMyFacilityUseCase,
    private val listMyFacilitiesUseCase: ListMyFacilitiesUseCase,
    private val getMyFacilityUseCase: GetMyFacilityUseCase,
    private val updateMyFacilityUseCase: UpdateMyFacilityUseCase,
    private val deleteMyFacilityUseCase: DeleteMyFacilityUseCase,
    private val ownershipGuard: OwnershipGuard,
) {

    @PostMapping
    @PreAuthorize("hasRole('FACILITY_OWNER')")
    fun register(
        @RequestBody request: RegisterFacilityRequest,
        uriBuilder: UriComponentsBuilder,
    ): ResponseEntity<MyFacilityResponse> {
        val authUserId = ownershipGuard.authUserId()
        val response = registerMyFacilityUseCase.execute(request.toCommand(authUserId))
        val location = uriBuilder.path("/api/b2b/facilities/{id}").buildAndExpand(response.id).toUri()
        return ResponseEntity.created(location).body(response)
    }

    @GetMapping
    @PreAuthorize("hasRole('FACILITY_OWNER')")
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<Page<MyFacilityResponse>> {
        val pageable = PageRequest.of(page, size)
        return ResponseEntity.ok(listMyFacilitiesUseCase.execute(principal.id, pageable))
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('FACILITY_OWNER')")
    fun get(
        @PathVariable id: String,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<MyFacilityResponse> =
        ResponseEntity.ok(getMyFacilityUseCase.execute(id, principal.id))

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('FACILITY_OWNER')")
    fun update(
        @PathVariable id: String,
        @RequestBody patch: Map<String, String>,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<MyFacilityResponse> {
        val command = UpdateMyFacilityCommand(facilityId = id, patch = patch, authUserId = principal.id)
        return ResponseEntity.ok(updateMyFacilityUseCase.execute(command))
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('FACILITY_OWNER')")
    fun delete(
        @PathVariable id: String,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<Void> {
        deleteMyFacilityUseCase.execute(DeleteMyFacilityCommand(facilityId = id, authUserId = principal.id))
        return ResponseEntity.noContent().build()
    }
}
