package com.sportsapp.domain.weather.gateway

import com.sportsapp.domain.weather.vo.Forecast

/**
 * 위경도 좌표에 대한 단기예보를 조회하는 외부 Gateway.
 * 구현체는 기상청 단기예보(getVilageFcst) API(또는 동일 스키마 mock)를 호출합니다.
 */
interface WeatherGateway {
    fun shortForecast(lat: Double, lng: Double): Forecast
}
