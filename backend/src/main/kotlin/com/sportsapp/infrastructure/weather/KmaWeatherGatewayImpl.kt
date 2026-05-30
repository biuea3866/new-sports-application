package com.sportsapp.infrastructure.weather

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.sportsapp.domain.weather.Forecast
import com.sportsapp.domain.weather.ForecastSlot
import com.sportsapp.domain.weather.PrecipitationType
import com.sportsapp.domain.weather.SkyState
import com.sportsapp.domain.weather.WeatherGateway
import com.sportsapp.infrastructure.external.ExternalRestClientFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 기상청 단기예보(getVilageFcst) 로 위경도 좌표의 예보를 조회합니다.
 * base-url 은 service 경로까지(.../VilageFcstInfoService_2.0), path 는 /getVilageFcst.
 * api-key(서비스키)는 data.go.kr 의 decoded 키를 사용합니다(RestClient 가 인코딩).
 */
@Component
class KmaWeatherGatewayImpl(
    restClientFactory: ExternalRestClientFactory,
    private val properties: WeatherProperties,
) : WeatherGateway {

    private val restClient: RestClient = restClientFactory.create(properties.baseUrl)

    override fun shortForecast(lat: Double, lng: Double): Forecast {
        val grid = GridConverter.toGrid(lat, lng)
        val base = BaseTimeResolver.resolve(ZonedDateTime.now(SEOUL))
        val response = restClient.get()
            .uri { builder ->
                builder.path("/getVilageFcst")
                    .queryParam("serviceKey", properties.apiKey)
                    .queryParam("dataType", "JSON")
                    .queryParam("numOfRows", NUM_OF_ROWS)
                    .queryParam("pageNo", 1)
                    .queryParam("base_date", base.baseDate)
                    .queryParam("base_time", base.baseTime)
                    .queryParam("nx", grid.nx)
                    .queryParam("ny", grid.ny)
                    .build()
            }
            .retrieve()
            .body(KmaForecastResponse::class.java)
        return (response?.items() ?: emptyList()).toForecast()
    }

    companion object {
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        private const val NUM_OF_ROWS = 300
    }
}

private fun List<KmaItem>.toForecast(): Forecast {
    val slots = groupBy { it.fcstDate to it.fcstTime }
        .map { (key, items) ->
            val byCategory = items.associate { it.category to it.fcstValue }
            ForecastSlot(
                date = key.first,
                time = key.second,
                temperature = byCategory["TMP"]?.toDoubleOrNull(),
                sky = byCategory["SKY"]?.let { SkyState.fromCode(it) },
                precipitationType = byCategory["PTY"]?.let { PrecipitationType.fromCode(it) },
                precipitationProbability = byCategory["POP"]?.toIntOrNull(),
                humidity = byCategory["REH"]?.toIntOrNull(),
                windSpeed = byCategory["WSD"]?.toDoubleOrNull(),
            )
        }
        .sortedWith(compareBy({ it.date }, { it.time }))
    return Forecast(slots = slots)
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class KmaForecastResponse(
    val response: KmaResponseEnvelope? = null,
) {
    fun items(): List<KmaItem> = response?.body?.items?.item ?: emptyList()
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class KmaResponseEnvelope(
    val body: KmaResponseBody? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KmaResponseBody(
    val items: KmaItemList? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KmaItemList(
    val item: List<KmaItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KmaItem(
    val category: String = "",
    val fcstDate: String = "",
    val fcstTime: String = "",
    val fcstValue: String = "",
)
