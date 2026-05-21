package com.sportsapp.application.facility

import com.sportsapp.domain.facility.FacilityOwnerDomainService
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class ListMyFacilitiesUseCase(
    private val facilityOwnerDomainService: FacilityOwnerDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(ownerUserId: Long, page: Int, size: Int): Page<FacilityResponse> {
        val pageable = PageRequest.of(page, size)
        return facilityOwnerDomainService.listByOwner(ownerUserId, pageable)
            .map { FacilityResponse.of(it) }
    }
}
