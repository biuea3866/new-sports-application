package com.sportsapp.domain.facility.dto

data class RegionTypeCount(
    val sidoCode: String,
    val sidoName: String,
    val sigunguCode: String,
    val sigunguName: String,
    val type: String,
    val count: Long,
)
