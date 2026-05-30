package com.sportsapp.presentation.facility

import com.sportsapp.application.facility.FacilityCriteria
import com.sportsapp.application.facility.FacilityResponse
import com.sportsapp.application.facility.FindNearbyFacilitiesUseCase
import com.sportsapp.application.facility.GetFacilityUseCase
import com.sportsapp.application.facility.GetGuTypeStatsUseCase
import com.sportsapp.application.facility.GuTypeCountResponse
import com.sportsapp.application.facility.ListFacilitiesUseCase
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/facilities")
@Profile("!test-jpa")
class FacilityApiController(
    private val listFacilitiesUseCase: ListFacilitiesUseCase,
    private val getFacilityUseCase: GetFacilityUseCase,
    private val getGuTypeStatsUseCase: GetGuTypeStatsUseCase,
    private val findNearbyFacilitiesUseCase: FindNearbyFacilitiesUseCase,
) {
    @GetMapping("/near")
    fun findNearby(
        @RequestParam lat: Double,
        @RequestParam lng: Double,
        @RequestParam(defaultValue = "2000") radiusMeters: Double,
    ): ResponseEntity<List<FacilityResponse>> =
        ResponseEntity.ok(findNearbyFacilitiesUseCase.execute(lat, lng, radiusMeters))

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

    @GetMapping("/stats/gu-type")
    fun getGuTypeStats(): ResponseEntity<List<GuTypeCountResponse>> =
        ResponseEntity.ok(getGuTypeStatsUseCase.execute())

    @GetMapping("/{id}")
    fun getFacility(@PathVariable id: String): ResponseEntity<FacilityResponse> =
        ResponseEntity.ok(getFacilityUseCase.execute(id))
}
