package com.sportsapp.presentation.weather.controller

import com.sportsapp.application.weather.ForecastResponse
import com.sportsapp.application.weather.usecase.GetForecastUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/weather")
class WeatherApiController(
    private val getForecastUseCase: GetForecastUseCase,
) {
    @GetMapping
    fun getForecast(
        @RequestParam lat: Double,
        @RequestParam lng: Double,
    ): ResponseEntity<ForecastResponse> =
        ResponseEntity.ok(getForecastUseCase.execute(lat, lng))
}
