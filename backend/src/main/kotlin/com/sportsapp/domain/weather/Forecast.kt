package com.sportsapp.domain.weather

data class Forecast(
    val slots: List<ForecastSlot>,
)

data class ForecastSlot(
    val date: String, // YYYYMMDD
    val time: String, // HHmm
    val temperature: Double?, // 기온(℃, TMP)
    val sky: SkyState?, // 하늘 상태(SKY)
    val precipitationType: PrecipitationType?, // 강수 형태(PTY)
    val precipitationProbability: Int?, // 강수 확률(%, POP)
    val humidity: Int?, // 습도(%, REH)
    val windSpeed: Double?, // 풍속(m/s, WSD)
)

enum class SkyState(val code: String) {
    CLEAR("1"),
    MOSTLY_CLOUDY("3"),
    CLOUDY("4"),
    ;

    companion object {
        fun fromCode(code: String): SkyState? = entries.find { it.code == code }
    }
}

enum class PrecipitationType(val code: String) {
    NONE("0"),
    RAIN("1"),
    RAIN_SNOW("2"),
    SNOW("3"),
    SHOWER("4"),
    ;

    companion object {
        fun fromCode(code: String): PrecipitationType? = entries.find { it.code == code }
    }
}
