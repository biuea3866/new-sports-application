package com.sportsapp.presentation.airquality.controller

import com.sportsapp.application.airquality.dto.AirQualityResponse
import com.sportsapp.application.airquality.usecase.GetAirQualityUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/air-quality")
class AirQualityApiController(
    private val getAirQualityUseCase: GetAirQualityUseCase,
) {

    /** 좌표 기준 실시간 대기질을 조회한다. Gateway가 degrade(empty)돼도 200으로 응답한다(값 null·UNKNOWN 등급). */
    @GetMapping
    fun getAirQuality(
        @RequestParam(required = false) lat: Double?,
        @RequestParam(required = false) lng: Double?,
    ): ResponseEntity<AirQualityResponse> {
        val latitude = requireNotNull(lat) { "lat is required" }
        val longitude = requireNotNull(lng) { "lng is required" }
        val airQuality = getAirQualityUseCase.execute(latitude, longitude)
        return ResponseEntity.ok(AirQualityResponse.of(airQuality))
    }
}
