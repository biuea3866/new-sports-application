package com.sportsapp.infrastructure.persistence.facility

import com.sportsapp.domain.facility.Facility
import com.sportsapp.domain.facility.FacilityRepository
import com.sportsapp.domain.facility.GuTypeCount
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.geo.Distance
import org.springframework.data.geo.Metrics
import org.springframework.data.geo.Point
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
@Profile("!test-jpa")
class FacilityRepositoryImpl(
    private val facilityMongoRepository: FacilityMongoRepository,
    private val mongoTemplate: MongoTemplate,
) : FacilityRepository {

    override fun save(facility: Facility): Facility =
        facilityMongoRepository.save(facility)

    override fun saveAll(facilities: List<Facility>): List<Facility> =
        facilityMongoRepository.saveAll(facilities)

    override fun findById(id: String): Facility? =
        facilityMongoRepository.findByIdOrNull(id)?.takeIf { !it.isDeleted }

    override fun findByCode(code: String): Facility? =
        facilityMongoRepository.findByCodeAndDeletedAtIsNull(code)

    override fun upsertByCode(facility: Facility): Facility {
        val query = Query(Criteria.where("code").`is`(facility.code).and("deletedAt").isNull)
        val update = Update()
            .set("name", facility.name)
            .set("gu", facility.gu)
            .set("type", facility.type)
            .set("address", facility.address)
            .set("location", facility.location)
            .set("parking", facility.parking)
            .set("tel", facility.tel)
            .set("home_page", facility.homePage)
            .set("edu_yn", facility.eduYn)
            .set("meta", facility.meta)
        mongoTemplate.upsert(query, update, Facility::class.java)
        return facilityMongoRepository.findByCodeAndDeletedAtIsNull(facility.code)
            ?: error("upsert 후 code=${facility.code} 조회 실패")
    }

    override fun findAllByGu(gu: String): List<Facility> =
        facilityMongoRepository.findAllByGuAndDeletedAtIsNull(gu)

    override fun findAllByGuAndType(gu: String, type: String): List<Facility> =
        facilityMongoRepository.findAllByGuAndTypeAndDeletedAtIsNull(gu, type)

    override fun findNear(lat: Double, lng: Double, maxDistanceMeters: Double): List<Facility> {
        val point = Point(lng, lat)
        val distanceKm = maxDistanceMeters / METERS_PER_KM
        val distance = Distance(distanceKm, Metrics.KILOMETERS)
        return facilityMongoRepository.findByLocationNearAndDeletedAtIsNull(point, distance)
            .content
            .map { it.content }
    }

    override fun findAll(gu: String?, type: String?, pageable: Pageable): Page<Facility> = when {
        gu != null && type != null -> facilityMongoRepository.findAllByGuAndTypeAndDeletedAtIsNull(gu, type, pageable)
        gu != null -> facilityMongoRepository.findAllByGuAndDeletedAtIsNull(gu, pageable)
        type != null -> facilityMongoRepository.findAllByTypeAndDeletedAtIsNull(type, pageable)
        else -> facilityMongoRepository.findAllByDeletedAtIsNull(pageable)
    }

    override fun aggregateGuType(): List<GuTypeCount> {
        val aggregation = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("deletedAt").isNull),
            Aggregation.group("gu", "type").count().`as`("count"),
            Aggregation.project("count")
                .and("_id.gu").`as`("gu")
                .and("_id.type").`as`("type"),
        )
        return mongoTemplate.aggregate(aggregation, "facilities", GuTypeCount::class.java).mappedResults
    }

    companion object {
        private const val METERS_PER_KM = 1000.0
    }
}
