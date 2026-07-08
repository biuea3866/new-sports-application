package com.sportsapp.application.weather.usecase

import com.sportsapp.domain.weather.service.WeatherDomainService
import com.sportsapp.domain.weather.vo.Forecast
import org.springframework.stereotype.Service

@Service
class GetForecastUseCase(
    private val weatherDomainService: WeatherDomainService,
) {
    fun execute(lat: Double, lng: Double): Forecast =
        weatherDomainService.shortForecast(lat, lng)
}
