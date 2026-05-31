package com.sportsapp.infrastructure.weather.gateway

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "external.weather")
data class WeatherProperties(
    val baseUrl: String = "http://localhost:9102",
    val apiKey: String = "",
)
