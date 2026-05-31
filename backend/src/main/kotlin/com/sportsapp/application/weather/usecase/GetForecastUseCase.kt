package com.sportsapp.application.weather.usecase

import com.sportsapp.application.weather.ForecastResponse
import com.sportsapp.domain.weather.service.WeatherDomainService
import org.springframework.stereotype.Service

@Service
class GetForecastUseCase(
    private val weatherDomainService: WeatherDomainService,
) {
    fun execute(lat: Double, lng: Double): ForecastResponse =
        ForecastResponse.of(weatherDomainService.shortForecast(lat, lng))
}
