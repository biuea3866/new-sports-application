package com.sportsapp.domain.facility

class Facility(
    val id: String?,
    val code: String,
    val name: String,
    val gu: String,
    val type: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val parking: Boolean,
    val tel: String,
    val homePage: String,
    val eduYn: Boolean,
    val meta: Map<String, String>,
) {

    fun updateMeta(patch: Map<String, String>): Facility =
        Facility(
            id = id,
            code = code,
            name = name,
            gu = gu,
            type = type,
            address = address,
            lat = lat,
            lng = lng,
            parking = parking,
            tel = tel,
            homePage = homePage,
            eduYn = eduYn,
            meta = meta + patch,
        )

    companion object {
        fun create(attributes: FacilityAttributes): Facility {
            if (attributes.code.isBlank()) throw InvalidFacilityException("code must not be blank")
            return Facility(
                id = null,
                code = attributes.code,
                name = attributes.name,
                gu = attributes.gu,
                type = attributes.type,
                address = attributes.address,
                lat = attributes.lat,
                lng = attributes.lng,
                parking = attributes.parking,
                tel = attributes.tel,
                homePage = attributes.homePage,
                eduYn = attributes.eduYn,
                meta = attributes.meta,
            )
        }
    }
}
