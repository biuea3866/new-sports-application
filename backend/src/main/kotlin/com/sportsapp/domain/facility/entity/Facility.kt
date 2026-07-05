package com.sportsapp.domain.facility.entity

import com.sportsapp.domain.common.BaseDocument
import com.sportsapp.domain.facility.exception.InvalidFacilityException
import com.sportsapp.domain.facility.vo.FacilityAttributes
import com.sportsapp.domain.facility.vo.FacilityRegion
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
    CompoundIndex(name = "idx_gu_type", def = "{'gu': 1, 'type': 1}"),
    CompoundIndex(name = "idx_sido_sigungu_type", def = "{'sido_code': 1, 'sigungu_code': 1, 'type': 1}"),
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
    ownerUserId: Long? = null,
    sidoCode: String?,
    sidoName: String?,
    sigunguCode: String?,
    sigunguName: String?,
) : BaseDocument() {

    @Field("owner_user_id")
    var ownerUserId: Long? = ownerUserId
        private set

    // 레거시(마이그레이션 이전) 문서는 region 필드가 없다 — 재구성 시 UNSPECIFIED로 보정한다.
    @Field("sido_code")
    var sidoCode: String = sidoCode ?: FacilityRegion.UNSPECIFIED.sidoCode
        private set

    @Field("sido_name")
    var sidoName: String = sidoName ?: FacilityRegion.UNSPECIFIED.sidoName
        private set

    @Field("sigungu_code")
    var sigunguCode: String = sigunguCode ?: FacilityRegion.UNSPECIFIED.sigunguCode
        private set

    @Field("sigungu_name")
    var sigunguName: String = sigunguName ?: FacilityRegion.UNSPECIFIED.sigunguName
        private set

    val lat: Double get() = location.y
    val lng: Double get() = location.x

    fun assignOwner(userId: Long) {
        check(ownerUserId == null) { "Facility already has an owner" }
        ownerUserId = userId
    }

    fun isOwnedBy(userId: Long): Boolean = ownerUserId == userId

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
            sidoCode = sidoCode,
            sidoName = sidoName,
            sigunguCode = sigunguCode,
            sigunguName = sigunguName,
        )

    fun assignRegion(region: FacilityRegion): Facility =
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
            meta = meta,
            ownerUserId = ownerUserId,
            sidoCode = region.sidoCode,
            sidoName = region.sidoName,
            sigunguCode = region.sigunguCode,
            sigunguName = region.sigunguName,
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
                sidoCode = attributes.region.sidoCode,
                sidoName = attributes.region.sidoName,
                sigunguCode = attributes.region.sigunguCode,
                sigunguName = attributes.region.sigunguName,
            )
        }
    }
}
