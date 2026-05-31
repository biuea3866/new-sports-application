package com.sportsapp.domain.weather

import org.springframework.stereotype.Service

@Service
class WeatherDomainService(
    private val weatherGateway: WeatherGateway,
) {
    fun shortForecast(lat: Double, lng: Double): Forecast =
        weatherGateway.shortForecast(lat, lng)
}
