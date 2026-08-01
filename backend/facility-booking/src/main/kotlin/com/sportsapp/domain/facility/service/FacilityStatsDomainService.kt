package com.sportsapp.domain.facility.service

import com.sportsapp.domain.facility.dto.GuTypeCount
import com.sportsapp.domain.facility.dto.RegionTypeCount
import com.sportsapp.domain.facility.repository.FacilityRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

/**
 * 시설 통계 집계(구·유형별, 시도·시군구·유형별) 조회.
 * [FacilityDomainService]에서 분리했다 — CRUD/등록과 무관한 집계 전용 책임이라
 * 별도 클래스로 뺐다(TooManyFunctions 15/11 해소).
 */
@Service
@Profile("!test-jpa")
class FacilityStatsDomainService(
    private val facilityRepository: FacilityRepository,
) {
    fun aggregateGuType(): List<GuTypeCount> =
        facilityRepository.aggregateGuType()

    fun aggregateRegionType(): List<RegionTypeCount> =
        facilityRepository.aggregateRegionType()
}
