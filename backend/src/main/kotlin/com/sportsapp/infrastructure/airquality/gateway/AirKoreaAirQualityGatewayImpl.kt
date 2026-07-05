package com.sportsapp.infrastructure.airquality.gateway

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.sportsapp.domain.airquality.gateway.AirQualityGateway
import com.sportsapp.domain.airquality.repository.AirQualityMeasurementCache
import com.sportsapp.domain.airquality.vo.AirQualityMeasurement
import com.sportsapp.infrastructure.external.ExternalRestClientFactory
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField

/**
 * 에어코리아(한국환경공단) 대기질 3단계 체인을 캡슐화한다:
 * getTMStdrCrdnt(umdName) → getNearbyMsrstnList(tmX,tmY) → getMsrstnAcctoRltmMesureDnsty(stationName).
 * 캐시 히트 시 체인을 건너뛰고, 임의 단계 실패·타임아웃은 예외를 전파하지 않고
 * [AirQualityMeasurement.empty]로 degrade 한다(레디스 키 계약 §4·§5).
 */
@Component
class AirKoreaAirQualityGatewayImpl(
    restClientFactory: ExternalRestClientFactory,
    private val properties: AirQualityProperties,
    private val airQualityMeasurementCache: AirQualityMeasurementCache,
) : AirQualityGateway {

    private val restClient: RestClient = restClientFactory.create(properties.baseUrl)
    private val logger = LoggerFactory.getLogger(AirKoreaAirQualityGatewayImpl::class.java)

    override fun current(lat: Double, lng: Double): AirQualityMeasurement {
        val gridKey = AirQualityGridKey.of(lat, lng)
        val cached = airQualityMeasurementCache.findBy(gridKey)
        if (cached != null) {
            return cached
        }
        val measurement = fetchFromChain(gridKey)
        airQualityMeasurementCache.save(gridKey, measurement)
        return measurement
    }

    private fun fetchFromChain(gridKey: String): AirQualityMeasurement {
        try {
            val coordinate = fetchTmCoordinate(gridKey) ?: return AirQualityMeasurement.empty()
            val stationName = fetchNearbyStation(coordinate) ?: return AirQualityMeasurement.empty()
            return fetchRealtimeMeasure(stationName) ?: AirQualityMeasurement.empty()
        } catch (exception: RestClientException) {
            logger.warn("air quality chain fetch failed (gridKey={}): {}", gridKey, exception.message)
            return AirQualityMeasurement.empty()
        }
    }

    private fun fetchTmCoordinate(gridKey: String): AirKoreaTmCoordinateItem? {
        val response = restClient.get()
            .uri { builder ->
                builder.path("/B552584/MsrstnInfoInqireSvc/getTMStdrCrdnt")
                    .queryParam("serviceKey", properties.apiKey)
                    .queryParam("returnType", "json")
                    .queryParam("umdName", gridKey)
                    .build()
            }
            .retrieve()
            .body(object : ParameterizedTypeReference<AirKoreaEnvelope<AirKoreaTmCoordinateItem>>() {})
        return response.itemsOrNull(gridKey, "getTMStdrCrdnt")?.first()
    }

    private fun fetchNearbyStation(coordinate: AirKoreaTmCoordinateItem): String? {
        val response = restClient.get()
            .uri { builder ->
                builder.path("/B552584/MsrstnInfoInqireSvc/getNearbyMsrstnList")
                    .queryParam("serviceKey", properties.apiKey)
                    .queryParam("returnType", "json")
                    .queryParam("tmX", coordinate.tmX)
                    .queryParam("tmY", coordinate.tmY)
                    .build()
            }
            .retrieve()
            .body(object : ParameterizedTypeReference<AirKoreaEnvelope<AirKoreaNearbyStationItem>>() {})
        return response.itemsOrNull(coordinate.tmX, "getNearbyMsrstnList")?.first()?.stationName
    }

    private fun fetchRealtimeMeasure(stationName: String): AirQualityMeasurement? {
        val response = restClient.get()
            .uri { builder ->
                builder.path("/B552584/ArpltnInforInqireSvc/getMsrstnAcctoRltmMesureDnsty")
                    .queryParam("serviceKey", properties.apiKey)
                    .queryParam("returnType", "json")
                    .queryParam("stationName", stationName)
                    .build()
            }
            .retrieve()
            .body(object : ParameterizedTypeReference<AirKoreaEnvelope<AirKoreaRealtimeMeasureItem>>() {})
        val item = response.itemsOrNull(stationName, "getMsrstnAcctoRltmMesureDnsty")?.first() ?: return null
        return AirQualityMeasurement(
            pm10 = item.pm10Value.toIntOrNull(),
            pm25 = item.pm25Value.toIntOrNull(),
            stationName = item.stationName,
            measuredAt = parseDataTime(item.dataTime),
        )
    }

    private fun <T> AirKoreaEnvelope<T>?.itemsOrNull(key: String, stage: String): List<T>? {
        val resultCode = this?.resultCode()
        if (resultCode != SUCCESS_RESULT_CODE) {
            logger.warn("air quality {} result code not success (key={}, resultCode={})", stage, key, resultCode)
            return null
        }
        val items = this?.items().orEmpty()
        if (items.isEmpty()) {
            logger.warn("air quality {} returned empty items (key={})", stage, key)
            return null
        }
        return items
    }

    private fun parseDataTime(dataTime: String): ZonedDateTime? {
        if (dataTime.isBlank()) {
            return null
        }
        return try {
            val parsed = DATA_TIME_FORMATTER.parse(dataTime)
            ZonedDateTime.of(
                parsed.get(ChronoField.YEAR),
                parsed.get(ChronoField.MONTH_OF_YEAR),
                parsed.get(ChronoField.DAY_OF_MONTH),
                parsed.get(ChronoField.HOUR_OF_DAY),
                parsed.get(ChronoField.MINUTE_OF_HOUR),
                0,
                0,
                SEOUL,
            )
        } catch (exception: java.time.format.DateTimeParseException) {
            logger.warn("air quality dataTime parse failed (dataTime={}): {}", dataTime, exception.message)
            null
        }
    }

    companion object {
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        private const val SUCCESS_RESULT_CODE = "00"
        private val DATA_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AirKoreaEnvelope<T>(
    val response: AirKoreaResponse<T>? = null,
) {
    fun resultCode(): String? = response?.header?.resultCode
    fun items(): List<T> = response?.body?.items?.item ?: emptyList()
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AirKoreaResponse<T>(
    val header: AirKoreaResponseHeader? = null,
    val body: AirKoreaResponseBody<T>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AirKoreaResponseHeader(
    val resultCode: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AirKoreaResponseBody<T>(
    val items: AirKoreaItems<T>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AirKoreaItems<T>(
    val item: List<T> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AirKoreaTmCoordinateItem(
    val tmX: String = "",
    val tmY: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AirKoreaNearbyStationItem(
    val stationName: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AirKoreaRealtimeMeasureItem(
    val stationName: String = "",
    val pm10Value: String = "",
    val pm25Value: String = "",
    val dataTime: String = "",
)
