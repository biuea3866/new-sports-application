package com.sportsapp.domain.facility.service

import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.repository.FacilityRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

/**
 * 좌표 기반 근접 시설 조회. [FacilityDomainService]에서 분리했다 — CRUD/등록과 무관한
 * 지리 조회 전용 책임이라 별도 클래스로 뺐다(TooManyFunctions 11/11 해소).
 */
@Service
@Profile("!test-jpa")
class FacilityGeoQueryDomainService(
    private val facilityRepository: FacilityRepository,
) {
    fun findNear(lat: Double, lng: Double, maxDistanceMeters: Double): List<Facility> =
        facilityRepository.findNear(lat, lng, maxDistanceMeters)
}
