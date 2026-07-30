package com.sportsapp.domain.facility.vo

/**
 * 시설이 속한 행정구역(시도·시군구) 값객체.
 * 주소 파싱·조회에 실패하면 [UNSPECIFIED]로 보존한다.
 */
data class FacilityRegion(
    val sidoCode: String,
    val sidoName: String,
    val sigunguCode: String,
    val sigunguName: String,
) {
    fun isUnspecified(): Boolean = sidoCode == UNSPECIFIED_SIDO

    companion object {
        const val UNSPECIFIED_SIDO = "00"
        const val UNSPECIFIED_SIGUNGU = "00000"

        val UNSPECIFIED = FacilityRegion(
            sidoCode = UNSPECIFIED_SIDO,
            sidoName = "미지정",
            sigunguCode = UNSPECIFIED_SIGUNGU,
            sigunguName = "미지정",
        )

        fun of(
            sidoCode: String,
            sidoName: String,
            sigunguCode: String,
            sigunguName: String,
        ): FacilityRegion = FacilityRegion(
            sidoCode = sidoCode,
            sidoName = sidoName,
            sigunguCode = sigunguCode,
            sigunguName = sigunguName,
        )
    }
}
