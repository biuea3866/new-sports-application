package com.sportsapp.infrastructure.facility.gateway

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "external.geocoding")
data class GeocodingProperties(
    val baseUrl: String = "http://localhost:9101",
    val apiKey: String = "",
)
