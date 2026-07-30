package com.sportsapp.presentation.facility.dto.response

import com.sportsapp.domain.facility.dto.RegionTypeCount

data class RegionTypeCountResponse(
    val sidoCode: String,
    val sidoName: String,
    val sigunguCode: String,
    val sigunguName: String,
    val type: String,
    val count: Long,
) {
    companion object {
        fun of(regionTypeCount: RegionTypeCount): RegionTypeCountResponse = RegionTypeCountResponse(
            sidoCode = regionTypeCount.sidoCode,
            sidoName = regionTypeCount.sidoName,
            sigunguCode = regionTypeCount.sigunguCode,
            sigunguName = regionTypeCount.sigunguName,
            type = regionTypeCount.type,
            count = regionTypeCount.count,
        )
    }
}
