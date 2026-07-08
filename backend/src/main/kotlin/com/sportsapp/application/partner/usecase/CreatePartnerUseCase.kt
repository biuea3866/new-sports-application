package com.sportsapp.application.partner.usecase

import com.sportsapp.application.partner.dto.CreatePartnerCommand
import com.sportsapp.application.partner.dto.CreatePartnerResponse
import com.sportsapp.domain.common.UserRoleName
import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.partner.service.PartnerDomainService
import com.sportsapp.domain.user.service.UserDomainService
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * B2B Partner 등록 오케스트레이션.
 *
 * 연동 User 는 실제 로그인 용도가 아니라 goods/ticketing 코어 UseCase 가 요구하는
 * ownerUserId 를 만족시키기 위한 대리 계정이다. syntheticEmail 은 유니크성만 보장하면 되고,
 * randomPassword 는 반환하지 않아 노출되지 않는다(register 내부에서 즉시 해싱되어 저장).
 */
@Service
class CreatePartnerUseCase(
    private val userDomainService: UserDomainService,
    private val partnerDomainService: PartnerDomainService,
    private val ownershipGuard: OwnershipGuard,
) {
    private val secureRandom = SecureRandom()

    @Transactional
    fun execute(command: CreatePartnerCommand): CreatePartnerResponse {
        val adminId = ownershipGuard.authUserId()
        val syntheticUser = userDomainService.register(generateSyntheticEmail(), generateRandomPassword())
        assignPartnerRoles(adminId, syntheticUser.id)
        val (partner, issuedApiKey) = partnerDomainService.createPartner(command.name, syntheticUser.id)
        return CreatePartnerResponse.of(partner, issuedApiKey)
    }

    private fun assignPartnerRoles(adminId: Long, userId: Long) {
        userDomainService.assignRole(adminId, userId, UserRoleName.GOODS_SELLER.name)
        userDomainService.assignRole(adminId, userId, UserRoleName.EVENT_HOST.name)
    }

    private fun generateSyntheticEmail(): String =
        "partner+${UUID.randomUUID()}@integration.local"

    private fun generateRandomPassword(): String {
        val bytes = ByteArray(PASSWORD_BYTE_LENGTH)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val PASSWORD_BYTE_LENGTH = 32
    }
}
