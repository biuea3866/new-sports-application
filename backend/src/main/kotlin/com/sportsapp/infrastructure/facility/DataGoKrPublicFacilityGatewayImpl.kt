package com.sportsapp.infrastructure.facility

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.sportsapp.domain.facility.PublicFacility
import com.sportsapp.domain.facility.PublicSportsFacilityGateway
import com.sportsapp.infrastructure.external.ExternalRestClientFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * data.go.kr 전국 공공체육시설 목록 조회.
 * api-key(서비스키)는 decoded 키를 사용합니다(RestClient 가 인코딩).
 */
@Component
class DataGoKrPublicFacilityGatewayImpl(
    restClientFactory: ExternalRestClientFactory,
    private val properties: PublicFacilityProperties,
) : PublicSportsFacilityGateway {

    private val restClient: RestClient = restClientFactory.create(properties.baseUrl)
    private val logger = LoggerFactory.getLogger(DataGoKrPublicFacilityGatewayImpl::class.java)

    override fun fetchPage(pageNo: Int, numOfRows: Int): List<PublicFacility> {
        return try {
            val response = restClient.get()
                .uri { builder ->
                    builder.path("/openapi/service/publicSportsFacility/getList")
                        .queryParam("serviceKey", properties.apiKey)
                        .queryParam("dataType", "JSON")
                        .queryParam("pageNo", pageNo)
                        .queryParam("numOfRows", numOfRows)
                        .build()
                }
                .retrieve()
                .body(PublicFacilityApiResponse::class.java)
            response?.items().orEmpty().map { it.toPublicFacility() }
        } catch (exception: RestClientException) {
            logger.warn("public facility fetch failed (pageNo={}): {}", pageNo, exception.message)
            emptyList()
        }
    }
}

private fun PublicFacilityItem.toPublicFacility(): PublicFacility = PublicFacility(
    externalId = cpId,
    name = facilNm,
    gu = gu,
    type = faciTy,
    address = roadAddr,
    lat = la.toDoubleOrNull(),
    lng = lo.toDoubleOrNull(),
    tel = telno,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PublicFacilityApiResponse(
    val response: PublicFacilityEnvelope? = null,
) {
    fun items(): List<PublicFacilityItem> = response?.body?.items?.item ?: emptyList()
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class PublicFacilityEnvelope(
    val body: PublicFacilityBody? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PublicFacilityBody(
    val items: PublicFacilityItemList? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PublicFacilityItemList(
    val item: List<PublicFacilityItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PublicFacilityItem(
    val cpId: String = "",
    val facilNm: String = "",
    val roadAddr: String = "",
    val la: String = "",
    val lo: String = "",
    val faciTy: String = "",
    val gu: String = "",
    val telno: String = "",
)
