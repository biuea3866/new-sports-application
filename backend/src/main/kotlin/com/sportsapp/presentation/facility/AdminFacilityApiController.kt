package com.sportsapp.presentation.facility

import com.sportsapp.application.facility.ImportLegacyFacilitiesResponse
import com.sportsapp.application.facility.ImportLegacyFacilitiesUseCase
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/facilities")
@Profile("!test-jpa")
class AdminFacilityApiController(
    private val importLegacyFacilitiesUseCase: ImportLegacyFacilitiesUseCase,
) {
    @PostMapping("/import")
    fun importLegacy(
        @RequestBody request: ImportLegacyFacilitiesRequest,
    ): ResponseEntity<ImportLegacyFacilitiesResponse> {
        val response = importLegacyFacilitiesUseCase.execute(request.toCommand())
        return ResponseEntity.ok(response)
    }
}
