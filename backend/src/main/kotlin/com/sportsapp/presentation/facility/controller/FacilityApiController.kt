package com.sportsapp.presentation.facility.controller

import com.sportsapp.application.facility.dto.FacilityCriteria
import com.sportsapp.application.facility.usecase.FindNearbyFacilitiesUseCase
import com.sportsapp.application.facility.usecase.GetFacilityUseCase
import com.sportsapp.application.facility.usecase.GetGuTypeStatsUseCase
import com.sportsapp.application.facility.usecase.GetRegionTypeStatsUseCase
import com.sportsapp.application.facility.usecase.ListFacilitiesUseCase
import com.sportsapp.presentation.facility.dto.response.FacilityResponse
import com.sportsapp.presentation.facility.dto.response.GuTypeCountResponse
import com.sportsapp.presentation.facility.dto.response.RegionTypeCountResponse
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
    private val getRegionTypeStatsUseCase: GetRegionTypeStatsUseCase,
    private val findNearbyFacilitiesUseCase: FindNearbyFacilitiesUseCase,
) {
    @GetMapping("/near")
    fun findNearby(
        @RequestParam lat: Double,
        @RequestParam lng: Double,
        @RequestParam(defaultValue = "2000") radiusMeters: Double,
    ): ResponseEntity<List<FacilityResponse>> =
        ResponseEntity.ok(findNearbyFacilitiesUseCase.execute(lat, lng, radiusMeters).map(FacilityResponse::of))

    @GetMapping
    fun listFacilities(
        @RequestParam(required = false) sidoCode: String?,
        @RequestParam(required = false) sigunguCode: String?,
        @RequestParam(required = false) gu: String?,
        @RequestParam(required = false) type: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): ResponseEntity<Page<FacilityResponse>> {
        val criteria = FacilityCriteria(sidoCode = sidoCode, sigunguCode = sigunguCode, gu = gu, type = type, page = page, size = size)
        return ResponseEntity.ok(listFacilitiesUseCase.execute(criteria).map { FacilityResponse.of(it) })
    }

    @GetMapping("/stats/gu-type")
    fun getGuTypeStats(): ResponseEntity<List<GuTypeCountResponse>> =
        ResponseEntity.ok(getGuTypeStatsUseCase.execute().map { GuTypeCountResponse.of(it) })

    @GetMapping("/stats/region-type")
    fun getRegionTypeStats(): ResponseEntity<List<RegionTypeCountResponse>> =
        ResponseEntity.ok(getRegionTypeStatsUseCase.execute().map { RegionTypeCountResponse.of(it) })

    @GetMapping("/{id}")
    fun getFacility(@PathVariable id: String): ResponseEntity<FacilityResponse> =
        ResponseEntity.ok(FacilityResponse.of(getFacilityUseCase.execute(id)))
}
