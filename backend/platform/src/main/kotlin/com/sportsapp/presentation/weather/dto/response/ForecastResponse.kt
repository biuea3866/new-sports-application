package com.sportsapp.presentation.weather.dto.response

import com.sportsapp.domain.weather.vo.Forecast
import com.sportsapp.domain.weather.vo.ForecastSlot

data class ForecastResponse(
    val slots: List<ForecastSlotResponse>,
) {
    companion object {
        fun of(forecast: Forecast): ForecastResponse =
            ForecastResponse(forecast.slots.map { ForecastSlotResponse.of(it) })
    }
}

data class ForecastSlotResponse(
    val date: String,
    val time: String,
    val temperature: Double?,
    val sky: String?,
    val precipitationType: String?,
    val precipitationProbability: Int?,
    val humidity: Int?,
    val windSpeed: Double?,
) {
    companion object {
        fun of(slot: ForecastSlot): ForecastSlotResponse = ForecastSlotResponse(
            date = slot.date,
            time = slot.time,
            temperature = slot.temperature,
            sky = slot.sky?.name,
            precipitationType = slot.precipitationType?.name,
            precipitationProbability = slot.precipitationProbability,
            humidity = slot.humidity,
            windSpeed = slot.windSpeed,
        )
    }
}
