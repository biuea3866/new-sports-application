package com.sportsapp.presentation.facility.controller

import com.sportsapp.application.facility.usecase.ImportLegacyFacilitiesUseCase
import com.sportsapp.application.facility.usecase.ImportPublicFacilitiesUseCase
import com.sportsapp.presentation.facility.dto.request.ImportLegacyFacilitiesRequest
import com.sportsapp.presentation.facility.dto.response.ImportLegacyFacilitiesResponse
import com.sportsapp.presentation.facility.dto.response.ImportPublicFacilitiesResponse
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
        val useCaseResult = importLegacyFacilitiesUseCase.execute(request.toCommand())
        return ResponseEntity.ok(ImportLegacyFacilitiesResponse.of(useCaseResult.result, useCaseResult.dryRun))
    }

    @PostMapping("/import-public")
    fun importPublic(
        @RequestParam(defaultValue = "10") maxPages: Int,
        @RequestParam(defaultValue = "100") numOfRows: Int,
    ): ResponseEntity<ImportPublicFacilitiesResponse> =
        ResponseEntity.ok(ImportPublicFacilitiesResponse.of(importPublicFacilitiesUseCase.execute(maxPages, numOfRows)))
}
