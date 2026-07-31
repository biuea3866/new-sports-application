package com.sportsapp.infrastructure.airquality.gateway

import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate
import java.util.Locale

/**
 * WGS84(EPSG:4326) 위경도 → 에어코리아(한국환경공단) TM 좌표계(EPSG:5181, Korea 2000 / Central Belt)
 * 변환기. [AirKoreaAirQualityGatewayImpl]이 getNearbyMsrstnList(tmX,tmY) 호출 직전 좌표 변환에 사용한다.
 *
 * EPSG 확정 근거(실서버 실측, 2026-07-06 — 추측이 아니라 실제 응답으로 검증):
 * 1. `getMsrstnList?addr=서울 중구` → 측정소 "중구" 위경도(dmX=37.564639(위도), dmY=126.975961(경도)).
 * 2. 위 위경도를 본 변환기(EPSG:5181 정의)로 변환하면 tmX=197876.17, tmY=451678.53.
 * 3. 그 tmX/tmY 로 실서버 `getNearbyMsrstnList` 를 호출하면 최근접 측정소가 "중구" 자기 자신
 *    (거리 tm=0.3km, 반올림 오차 수준)으로 반환된다 → EPSG:5181 이 정답.
 * 4. 동일 절차를 부산 해운대구 "좌동" 측정소(dmX=35.1708927, dmY=129.1741659 → tmX=398078.23,
 *    tmY=188219.47)로 재현해도 최근접 측정소가 "좌동"(tm=0.3km)으로 확인돼 재현성을 검증했다.
 *
 * EPSG:5181 proj4 정의: `+proj=tmerc +lat_0=38 +lon_0=127 +k=1 +x_0=200000 +y_0=500000 +ellps=GRS80 +units=m`.
 */
object AirKoreaTmProjection {

    private const val PROJECTION_SCALE_FACTOR = 1
    private const val LATITUDE_OF_ORIGIN = 38
    private const val CENTRAL_MERIDIAN = 127
    private const val FALSE_EASTING = 200000
    private const val FALSE_NORTHING = 500000
    private const val COORDINATE_DECIMAL_PLACES = "%.2f"

    private val crsFactory = CRSFactory()

    private val wgs84Crs = crsFactory.createFromParameters(
        "WGS84",
        "+proj=longlat +ellps=WGS84 +datum=WGS84 +no_defs",
    )

    private val airKoreaTmCrs = crsFactory.createFromParameters(
        "AIRKOREA_TM",
        "+proj=tmerc +lat_0=$LATITUDE_OF_ORIGIN +lon_0=$CENTRAL_MERIDIAN " +
            "+k=$PROJECTION_SCALE_FACTOR +x_0=$FALSE_EASTING +y_0=$FALSE_NORTHING +ellps=GRS80 +units=m +no_defs",
    )

    private val transform = CoordinateTransformFactory().createTransform(wgs84Crs, airKoreaTmCrs)

    /** [lat], [lng](WGS84)를 에어코리아 TM 좌표(EPSG:5181)로 변환한다. */
    fun toTm(lat: Double, lng: Double): AirKoreaTmCoordinate {
        val source = ProjCoordinate(lng, lat)
        val target = ProjCoordinate()
        transform.transform(source, target)
        return AirKoreaTmCoordinate(
            tmX = String.format(Locale.ROOT, COORDINATE_DECIMAL_PLACES, target.x),
            tmY = String.format(Locale.ROOT, COORDINATE_DECIMAL_PLACES, target.y),
        )
    }
}

/** 에어코리아 TM 좌표 값 객체. [getNearbyMsrstnList] 쿼리 파라미터(tmX,tmY)로 그대로 전달된다. */
data class AirKoreaTmCoordinate(val tmX: String, val tmY: String)
