package com.sportsapp.infrastructure.facility.region

import org.springframework.data.jpa.repository.JpaRepository

interface RegionJpaRepository : JpaRepository<RegionJpaEntity, Long> {
    fun findBySidoNameAndSigunguName(sidoName: String, sigunguName: String): RegionJpaEntity?
    fun findBySigunguName(sigunguName: String): List<RegionJpaEntity>
}
