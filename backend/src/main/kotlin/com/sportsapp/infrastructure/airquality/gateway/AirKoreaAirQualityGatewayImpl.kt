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
 * 에어코리아(한국환경공단) 대기질 2단계 체인을 캡슐화한다:
 * [AirKoreaTmProjection]으로 WGS84 좌표를 TM 좌표로 직접 변환 →
 * getNearbyMsrstnList(tmX,tmY) → getMsrstnAcctoRltmMesureDnsty(stationName, dataTerm=DAILY).
 * 캐시 히트 시 체인을 건너뛰고, 임의 단계 실패·타임아웃은 예외를 전파하지 않고
 * [AirQualityMeasurement.empty]로 degrade 한다(레디스 키 계약 §4·§5).
 *
 * `getTMStdrCrdnt`(umdName/addr → TM 좌표) API는 더 이상 호출하지 않는다 — 그 API는
 * 동/읍면동 "이름" → 좌표 조회용이라, 위경도 문자열(gridKey)을 이름으로 넘기면 실서버가
 * 항상 totalCount:0 을 반환해 체인이 항상 empty 로 degrade 되는 결함이 있었다(실서버 실측).
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
        val measurement = fetchFromChain(lat, lng)
        airQualityMeasurementCache.save(gridKey, measurement)
        return measurement
    }

    private fun fetchFromChain(lat: Double, lng: Double): AirQualityMeasurement {
        try {
            val coordinate = AirKoreaTmProjection.toTm(lat, lng)
            val stationName = fetchNearbyStation(coordinate) ?: return AirQualityMeasurement.empty()
            return fetchRealtimeMeasure(stationName) ?: AirQualityMeasurement.empty()
        } catch (exception: RestClientException) {
            logger.warn("air quality chain fetch failed (lat={}, lng={}): {}", lat, lng, exception.message)
            return AirQualityMeasurement.empty()
        }
    }

    private fun fetchNearbyStation(coordinate: AirKoreaTmCoordinate): String? {
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
                    .queryParam("ver", REALTIME_MEASURE_API_VERSION)
                    .queryParam("dataTerm", REALTIME_MEASURE_DATA_TERM)
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

        /**
         * getMsrstnAcctoRltmMesureDnsty 는 ver 파라미터가 없으면 응답에서 pm25Value 필드 자체를 누락한다
         * (실서버 실측 2026-07-06). ver=1.3 을 명시해 pm25Value 를 응답에 포함시킨다.
         */
        private const val REALTIME_MEASURE_API_VERSION = "1.3"

        /**
         * getMsrstnAcctoRltmMesureDnsty 는 dataTerm 파라미터가 없으면 필수 파라미터 누락
         * 오류(resultCode=11 NO_MANDATORY_REQUEST_PARAMETERS_ERROR)를 반환한다(실서버 실측 2026-07-06).
         * 최근 실시간 측정값만 필요하므로 DAILY(최근 24시간)를 지정하고 최신 1건([itemsOrNull]의 first())만 사용한다.
         */
        private const val REALTIME_MEASURE_DATA_TERM = "DAILY"
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
