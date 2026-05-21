package com.sportsapp.domain.facility

import com.sportsapp.domain.common.BaseDocument
import org.springframework.data.annotation.Id
import org.springframework.data.geo.Point
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field

@Document(collection = "facilities")
@CompoundIndexes(
    CompoundIndex(name = "idx_gu_type", def = "{'gu': 1, 'type': 1}")
)
class Facility(
    @Id
    val id: String?,
    @Indexed
    @Field("code")
    val code: String,
    @Field("name")
    val name: String,
    @Indexed
    @Field("gu")
    val gu: String,
    @Field("type")
    val type: String,
    @Field("address")
    val address: String,
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    @Field("location")
    val location: Point,
    @Field("parking")
    val parking: Boolean,
    @Field("tel")
    val tel: String,
    @Field("home_page")
    val homePage: String,
    @Field("edu_yn")
    val eduYn: Boolean,
    @Field("meta")
    val meta: Map<String, String>,
    @Field("owner_user_id")
    var ownerUserId: Long? = null,
) : BaseDocument() {

    val lat: Double get() = location.y
    val lng: Double get() = location.x

    fun assignOwner(userId: Long) {
        check(ownerUserId == null) { "Facility already has an owner" }
        ownerUserId = userId
    }

    fun requireOwnedBy(userId: Long) {
        if (ownerUserId != userId) throw FacilityNotOwnedByException(requireNotNull(id) { "facility id must not be null" }, userId)
    }

    fun updateInfo(
        name: String? = null,
        address: String? = null,
        operatingHours: String? = null,
        basePrice: Long? = null,
    ): Facility {
        if (basePrice != null) require(basePrice > 0) { "basePrice must be positive" }
        return Facility(
            id = id,
            code = code,
            name = name ?: this.name,
            gu = gu,
            type = type,
            address = address ?: this.address,
            location = location,
            parking = parking,
            tel = tel,
            homePage = homePage,
            eduYn = eduYn,
            meta = if (operatingHours != null || basePrice != null) {
                meta +
                    (if (operatingHours != null) mapOf("operating_hours" to operatingHours) else emptyMap()) +
                    (if (basePrice != null) mapOf("base_price" to basePrice.toString()) else emptyMap())
            } else {
                meta
            },
            ownerUserId = ownerUserId,
        )
    }

    fun updateMeta(patch: Map<String, String>): Facility =
        Facility(
            id = id,
            code = code,
            name = name,
            gu = gu,
            type = type,
            address = address,
            location = location,
            parking = parking,
            tel = tel,
            homePage = homePage,
            eduYn = eduYn,
            meta = meta + patch,
            ownerUserId = ownerUserId,
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
                location = Point(attributes.lng, attributes.lat),
                parking = attributes.parking,
                tel = attributes.tel,
                homePage = attributes.homePage,
                eduYn = attributes.eduYn,
                meta = attributes.meta,
            )
        }
    }
}
