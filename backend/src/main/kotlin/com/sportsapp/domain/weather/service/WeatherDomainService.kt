package com.sportsapp.domain.weather.service

import com.sportsapp.domain.weather.gateway.WeatherGateway
import com.sportsapp.domain.weather.vo.Forecast
import org.springframework.stereotype.Service

@Service
class WeatherDomainService(
    private val weatherGateway: WeatherGateway,
) {
    fun shortForecast(lat: Double, lng: Double): Forecast =
        weatherGateway.shortForecast(lat, lng)
}
