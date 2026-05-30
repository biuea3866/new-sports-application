package com.sportsapp.presentation.facility

import com.sportsapp.application.facility.ImportLegacyFacilitiesResponse
import com.sportsapp.application.facility.ImportLegacyFacilitiesUseCase
import com.sportsapp.application.facility.ImportPublicFacilitiesResponse
import com.sportsapp.application.facility.ImportPublicFacilitiesUseCase
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/facilities")
@Profile("!test-jpa")
class AdminFacilityApiController(
    private val importLegacyFacilitiesUseCase: ImportLegacyFacilitiesUseCase,
    private val importPublicFacilitiesUseCase: ImportPublicFacilitiesUseCase,
) {
    @PostMapping("/import")
    fun importLegacy(
        @RequestBody request: ImportLegacyFacilitiesRequest,
    ): ResponseEntity<ImportLegacyFacilitiesResponse> {
        val response = importLegacyFacilitiesUseCase.execute(request.toCommand())
        return ResponseEntity.ok(response)
    }

    @PostMapping("/import-public")
    fun importPublic(
        @RequestParam(defaultValue = "10") maxPages: Int,
        @RequestParam(defaultValue = "100") numOfRows: Int,
    ): ResponseEntity<ImportPublicFacilitiesResponse> =
        ResponseEntity.ok(importPublicFacilitiesUseCase.execute(maxPages, numOfRows))
}
