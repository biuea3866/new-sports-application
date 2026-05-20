package com.sportsapp.domain.facility

/**
 * 레거시 MAPSERVICE / WAYFINDINGSERVICE 컬렉션 한 행을 표현하는 불변 값 객체.
 * 인프라 레이어가 MongoDB 도큐먼트에서 읽어 도메인으로 전달한다.
 */
data class LegacyRow(
    val legacyId: String,
    val name: String,
    val gu: String,
    val type: String,
    val address: String,
    val ycode: String,
    val xcode: String,
    val parking: Boolean,
    val tel: String,
    val homePage: String,
    val eduYn: Boolean,
    val extraFields: Map<String, String>,
)
