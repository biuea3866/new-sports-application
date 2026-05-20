package com.sportsapp.presentation.facility

import com.sportsapp.application.facility.FacilityCriteria
import com.sportsapp.application.facility.FacilityResponse
import com.sportsapp.application.facility.ListFacilitiesUseCase
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/facilities")
class FacilityApiController(
    private val listFacilitiesUseCase: ListFacilitiesUseCase,
) {
    @GetMapping
    fun listFacilities(
        @RequestParam(required = false) gu: String?,
        @RequestParam(required = false) type: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): ResponseEntity<Page<FacilityResponse>> {
        val criteria = FacilityCriteria(gu = gu, type = type, page = page, size = size)
        return ResponseEntity.ok(listFacilitiesUseCase.execute(criteria))
    }
}
