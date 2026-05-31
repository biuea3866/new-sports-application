package com.sportsapp.infrastructure.weather.gateway

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * 위경도 → 기상청 단기예보 격자(nx, ny) 변환.
 * 기상청 LCC DFS 좌표계 공식. 서울시청(37.5665, 126.9780) → (60, 127).
 */
object GridConverter {
    private const val RE = 6371.00877 // 지구 반경(km)
    private const val GRID = 5.0 // 격자 간격(km)
    private const val SLAT1 = 30.0 // 표준 위도 1
    private const val SLAT2 = 60.0 // 표준 위도 2
    private const val OLON = 126.0 // 기준점 경도
    private const val OLAT = 38.0 // 기준점 위도
    private const val XO = 43.0 // 기준점 X 좌표
    private const val YO = 136.0 // 기준점 Y 좌표

    fun toGrid(lat: Double, lng: Double): Grid {
        val degrad = PI / 180.0
        val re = RE / GRID
        val slat1 = SLAT1 * degrad
        val slat2 = SLAT2 * degrad
        val olon = OLON * degrad
        val olat = OLAT * degrad

        var sn = tan(PI * 0.25 + slat2 * 0.5) / tan(PI * 0.25 + slat1 * 0.5)
        sn = ln(cos(slat1) / cos(slat2)) / ln(sn)
        var sf = tan(PI * 0.25 + slat1 * 0.5)
        sf = sf.pow(sn) * cos(slat1) / sn
        var ro = tan(PI * 0.25 + olat * 0.5)
        ro = re * sf / ro.pow(sn)

        var ra = tan(PI * 0.25 + lat * degrad * 0.5)
        ra = re * sf / ra.pow(sn)
        var theta = lng * degrad - olon
        if (theta > PI) theta -= 2.0 * PI
        if (theta < -PI) theta += 2.0 * PI
        theta *= sn

        val nx = floor(ra * sin(theta) + XO + 0.5).toInt()
        val ny = floor(ro - ra * cos(theta) + YO + 0.5).toInt()
        return Grid(nx = nx, ny = ny)
    }
}

data class Grid(
    val nx: Int,
    val ny: Int,
)
