package com.sportsapp.infrastructure.airquality.gateway

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "external.air-quality")
data class AirQualityProperties(
    val baseUrl: String = "http://localhost:9102",
    // weather/public-facility 와 동일한 DATA_GO_KR_SERVICE_KEY 를 공유(FR-3 동시 전환 규약).
    // application.yml 규약(WeatherProperties·PublicFacilityProperties 와 동일 기본값)과 일치시킨다.
    val apiKey: String = "mock-service-key",
)
