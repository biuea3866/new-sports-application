package com.sportsapp.infrastructure.facility.region

import com.sportsapp.domain.facility.vo.FacilityRegion
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

/**
 * `regions` 테이블 JPA 매핑 — 시도·시군구 표준코드 정적 참조 테이블.
 * Flyway seed로만 적재되며 애플리케이션에서 쓰기(save/update)를 수행하지 않는다.
 */
@Entity
@Table(name = "regions")
class RegionJpaEntity private constructor(
    @Column(name = "sido_code", nullable = false, length = 2)
    val sidoCode: String,

    @Column(name = "sido_name", nullable = false, length = 40)
    val sidoName: String,

    @Column(name = "sigungu_code", nullable = false, length = 5)
    val sigunguCode: String,

    @Column(name = "sigungu_name", nullable = false, length = 60)
    val sigunguName: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: ZonedDateTime,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: ZonedDateTime,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
        protected set

    fun toFacilityRegion(): FacilityRegion = FacilityRegion.of(
        sidoCode = sidoCode,
        sidoName = sidoName,
        sigunguCode = sigunguCode,
        sigunguName = sigunguName,
    )
}
