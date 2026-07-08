package com.sportsapp.infrastructure.weather.gateway

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "external.weather")
data class WeatherProperties(
    val baseUrl: String = "http://localhost:9102",
    // public-facility 와 동일한 DATA_GO_KR_SERVICE_KEY 를 공유(FR-3 동시 전환).
    // application.yml 규약(PublicFacilityProperties 와 동일 기본값)과 일치시킨다.
    val apiKey: String = "mock-service-key",
)
