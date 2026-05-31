package com.sportsapp.application.weather

import com.sportsapp.domain.weather.WeatherDomainService
import org.springframework.stereotype.Service

@Service
class GetForecastUseCase(
    private val weatherDomainService: WeatherDomainService,
) {
    fun execute(lat: Double, lng: Double): ForecastResponse =
        ForecastResponse.of(weatherDomainService.shortForecast(lat, lng))
}
