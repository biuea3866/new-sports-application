package com.sportsapp.application.user

import com.sportsapp.domain.common.exceptions.UnauthorizedException
import com.sportsapp.domain.facility.FacilityDomainService
import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.user.OperatorProfile
import com.sportsapp.domain.user.UserDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class GetOperatorProfileUseCase(
    private val userDomainService: UserDomainService,
    private val facilityDomainService: FacilityDomainService,
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: GetOperatorProfileCommand): OperatorProfileResponse {
        if (command.requestUserId != command.targetUserId) {
            throw UnauthorizedException("Cannot access profile of another user")
        }
        val user = userDomainService.findByIdWithRoles(command.targetUserId)
        val facilityCount = facilityDomainService.countByOwnerUserId(command.targetUserId)
        val activeProductCount = goodsDomainService.countActiveProductsByOwnerId(command.targetUserId)
        val activeTokenCount = userDomainService.countActiveTokensByUserId(command.targetUserId)
        val profile = OperatorProfile(
            userId = user.id,
            email = user.email,
            status = user.status,
            facilityCount = facilityCount,
            activeProductCount = activeProductCount,
            activeTokenCount = activeTokenCount,
        )
        return OperatorProfileResponse.of(profile)
    }
}
