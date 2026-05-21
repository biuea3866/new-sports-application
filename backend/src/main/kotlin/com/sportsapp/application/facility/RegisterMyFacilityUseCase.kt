package com.sportsapp.application.facility

import com.sportsapp.domain.facility.FacilityAttributes
import com.sportsapp.domain.facility.FacilityOwnerDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class RegisterMyFacilityUseCase(
    private val facilityOwnerDomainService: FacilityOwnerDomainService,
) {
    @Transactional
    fun execute(command: RegisterMyFacilityCommand): FacilityResponse {
        val attributes = FacilityAttributes(
            code = command.code,
            name = command.name,
            gu = command.gu,
            type = command.type,
            address = command.address,
            lat = command.lat,
            lng = command.lng,
            parking = command.parking,
            tel = command.tel,
            homePage = command.homePage,
            eduYn = command.eduYn,
            meta = command.meta,
        )
        val facility = facilityOwnerDomainService.registerForOwner(attributes, command.ownerUserId)
        return FacilityResponse.of(facility)
    }
}
