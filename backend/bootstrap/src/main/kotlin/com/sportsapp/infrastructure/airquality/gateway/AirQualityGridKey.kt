package com.sportsapp.infrastructure.airquality.gateway

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * 좌표→그리드키 변환 (레디스 키 계약 §1). [AirKoreaAirQualityGatewayImpl]이 캐시 조회·저장·1단계 API 호출에 사용한다.
 * 단일 위치 원칙(키 계약 §9) — gridKey 생성 로직은 이 객체 하나로만 둔다.
 */
object AirQualityGridKey {

    private const val SCALE = 3

    /** [lat],[lng]를 소수 3자리(HALF_UP)로 반올림해 `"{lat}_{lng}"` 형식의 그리드키를 만든다. */
    fun of(lat: Double, lng: Double): String {
        val roundedLat = BigDecimal.valueOf(lat).setScale(SCALE, RoundingMode.HALF_UP)
        val roundedLng = BigDecimal.valueOf(lng).setScale(SCALE, RoundingMode.HALF_UP)
        return String.format(Locale.ROOT, "%s_%s", roundedLat.toPlainString(), roundedLng.toPlainString())
    }
}
