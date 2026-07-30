package com.sportsapp.infrastructure.booking.gateway

import com.sportsapp.domain.booking.exception.SlotFacilityNotFoundException
import com.sportsapp.domain.booking.exception.UnauthorizedFacilityAccessException
import com.sportsapp.domain.booking.gateway.FacilityOwnershipGateway
import com.sportsapp.domain.facility.service.FacilityDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * FacilityOwnershipGateway 구현체.
 *
 * facility 도메인의 공개 계약인 FacilityDomainService를 통해 시설 존재와 소유권을 확인하고,
 * 위반 시 booking 도메인 예외를 던진다. facility 예외는 booking 밖으로 노출하지 않는다.
 * FacilityDomainService가 test-jpa 프로파일에서 비활성화되므로 동일 프로파일 조건을 따른다.
 */
@Component
@Profile("!test-jpa")
class FacilityOwnershipGatewayImpl(
    private val facilityDomainService: FacilityDomainService,
) : FacilityOwnershipGateway {

    override fun requireOwner(facilityId: String, userId: Long) {
        val facility = facilityDomainService.findBy(facilityId)
            ?: throw SlotFacilityNotFoundException(facilityId)
        if (!facility.isOwnedBy(userId)) {
            throw UnauthorizedFacilityAccessException(facilityId, userId)
        }
    }
}
