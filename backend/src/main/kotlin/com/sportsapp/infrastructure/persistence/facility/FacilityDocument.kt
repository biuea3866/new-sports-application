package com.sportsapp.infrastructure.persistence.facility

import com.sportsapp.domain.facility.Facility
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
data class FacilityDocument(
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
) {

    fun toDomain(): Facility =
        Facility(
            id = id,
            code = code,
            name = name,
            gu = gu,
            type = type,
            address = address,
            lat = location.y,
            lng = location.x,
            parking = parking,
            tel = tel,
            homePage = homePage,
            eduYn = eduYn,
            meta = meta,
        )

    companion object {
        fun fromDomain(facility: Facility): FacilityDocument =
            FacilityDocument(
                id = facility.id,
                code = facility.code,
                name = facility.name,
                gu = facility.gu,
                type = facility.type,
                address = facility.address,
                location = Point(facility.lng, facility.lat),
                parking = facility.parking,
                tel = facility.tel,
                homePage = facility.homePage,
                eduYn = facility.eduYn,
                meta = facility.meta,
            )
    }
}
