package com.sportsapp.infrastructure.facility.gateway

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "external.public-facility")
data class PublicFacilityProperties(
    val baseUrl: String = "http://localhost:9102",
    val apiKey: String = "mock-service-key",
)
