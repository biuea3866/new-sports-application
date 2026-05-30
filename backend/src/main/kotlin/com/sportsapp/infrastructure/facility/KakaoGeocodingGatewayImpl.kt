package com.sportsapp.infrastructure.facility

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.sportsapp.domain.facility.Coordinate
import com.sportsapp.domain.facility.GeocodingGateway
import com.sportsapp.infrastructure.external.ExternalRestClientFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * Kakao Local REST API 로 주소→좌표를 변환합니다.
 * 키(api-key)가 비어 있으면 base-url(docker-compose mock)을 그대로 호출합니다.
 */
@Component
class KakaoGeocodingGatewayImpl(
    restClientFactory: ExternalRestClientFactory,
    private val properties: GeocodingProperties,
) : GeocodingGateway {

    private val restClient: RestClient = restClientFactory.create(properties.baseUrl)
    private val logger = LoggerFactory.getLogger(KakaoGeocodingGatewayImpl::class.java)

    override fun geocode(address: String): Coordinate? {
        if (address.isBlank()) return null
        return try {
            val response = restClient.get()
                .uri { builder ->
                    builder.path("/v2/local/search/address.json")
                        .queryParam("query", address)
                        .build()
                }
                .headers { headers ->
                    if (properties.apiKey.isNotBlank()) {
                        headers.set("Authorization", "KakaoAK ${properties.apiKey}")
                    }
                }
                .retrieve()
                .body(KakaoAddressResponse::class.java)
            response?.toCoordinate()
        } catch (exception: RestClientException) {
            logger.warn("geocoding failed for address='{}': {}", address, exception.message)
            null
        }
    }
}

private fun KakaoAddressResponse.toCoordinate(): Coordinate? {
    val document = documents.firstOrNull() ?: return null
    val lat = document.y.toDoubleOrNull() ?: return null
    val lng = document.x.toDoubleOrNull() ?: return null
    return Coordinate(lat = lat, lng = lng)
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class KakaoAddressResponse(
    val documents: List<KakaoAddressDocument> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KakaoAddressDocument(
    val x: String = "",
    val y: String = "",
)
