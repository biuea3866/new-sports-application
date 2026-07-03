package com.sportsapp.domain.partner.service

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.common.exceptions.UnauthorizedException
import com.sportsapp.domain.partner.entity.ApiKeyStatus
import com.sportsapp.domain.partner.entity.Partner
import com.sportsapp.domain.partner.entity.PartnerApiKey
import com.sportsapp.domain.partner.entity.PartnerStatus
import com.sportsapp.domain.partner.exception.PartnerApiKeyInactiveException
import com.sportsapp.domain.partner.exception.PartnerNotFoundException
import com.sportsapp.domain.partner.gateway.ApiKeyGenerator
import com.sportsapp.domain.partner.repository.PartnerApiKeyRepository
import com.sportsapp.domain.partner.repository.PartnerRepository
import org.springframework.stereotype.Service

data class IssuedApiKey(val plainKey: String, val apiKey: PartnerApiKey) {
    val keyId: Long get() = requireNotNull(apiKey.id) { "PartnerApiKey id must exist after save" }
}
data class AuthenticatedPartner(val partnerId: Long, val linkedUserId: Long)

/**
 * Partner 라이프사이클(생성·키 발급/재발급/폐기·상태 전이·인증) 도메인 서비스.
 *
 * API Key 발급은 2-step으로 이루어진다: placeholder 해시로 저장해 id를 먼저 확보한 뒤
 * `partner_<id>_<random>` 평문의 해시로 재구성한 엔티티를 다시 저장한다.
 * `domain.mcp.service.McpTokenDomainService#issueToken`과 동일한 목적의 패턴이지만,
 * [PartnerApiKey]는 `keyHash`를 변경하는 메서드를 노출하지 않는 불변 필드이므로
 * `updateTokenHash` 대신 [PartnerApiKey.reconstitute]로 새 인스턴스를 만들어 재저장한다.
 */
@Service
class PartnerDomainService(
    private val partnerRepository: PartnerRepository,
    private val partnerApiKeyRepository: PartnerApiKeyRepository,
    private val apiKeyGenerator: ApiKeyGenerator,
) {

    fun createPartner(name: String, linkedUserId: Long): Pair<Partner, IssuedApiKey> {
        val partner = partnerRepository.save(Partner.create(name, linkedUserId))
        val partnerId = requireNotNull(partner.id) { "Partner id must exist after save" }
        return partner to issueKey(partnerId)
    }

    fun issueKey(partnerId: Long): IssuedApiKey {
        val randomPart = apiKeyGenerator.generateRandomPart()
        val keyId = saveWithPlaceholderHash(partnerId, randomPart)
        val plainKey = "$KEY_PREFIX${keyId}_$randomPart"
        val issued = partnerApiKeyRepository.save(activeApiKey(keyId, partnerId, apiKeyGenerator.hash(plainKey)))
        return IssuedApiKey(plainKey = plainKey, apiKey = issued)
    }

    fun reissueKey(partnerId: Long): IssuedApiKey {
        revokeActiveKeyIfExists(partnerId)
        return issueKey(partnerId)
    }

    fun revokeKey(partnerId: Long, keyId: Long) {
        val apiKey = partnerApiKeyRepository.findById(keyId)
            ?: throw ResourceNotFoundException("PartnerApiKey", keyId)
        apiKey.requireOwnedBy(partnerId)
        apiKey.revoke()
        partnerApiKeyRepository.save(apiKey)
    }

    fun changeStatus(partnerId: Long, status: PartnerStatus) {
        val partner = partnerRepository.findById(partnerId) ?: throw PartnerNotFoundException(partnerId)
        if (status == PartnerStatus.ACTIVE) partner.activate() else partner.suspend()
        partnerRepository.save(partner)
    }

    fun authenticate(keyId: Long, plainKey: String): AuthenticatedPartner {
        val apiKey = partnerApiKeyRepository.findById(keyId) ?: throw UnauthorizedException(INVALID_KEY_MESSAGE)
        validateApiKey(apiKey, plainKey)
        val partner = partnerRepository.findById(apiKey.partnerId) ?: throw PartnerNotFoundException(apiKey.partnerId)
        partner.validateActive()
        return AuthenticatedPartner(partnerId = requireNotNull(partner.id), linkedUserId = partner.linkedUserId)
    }

    private fun saveWithPlaceholderHash(partnerId: Long, randomPart: String): Long {
        val placeholderHash = apiKeyGenerator.hash("$PLACEHOLDER_PREFIX$randomPart")
        val saved = partnerApiKeyRepository.save(PartnerApiKey.create(partnerId, placeholderHash))
        return requireNotNull(saved.id) { "PartnerApiKey id must exist after save" }
    }

    private fun activeApiKey(keyId: Long, partnerId: Long, keyHash: String): PartnerApiKey =
        PartnerApiKey.reconstitute(
            id = keyId,
            partnerId = partnerId,
            keyHash = keyHash,
            status = ApiKeyStatus.ACTIVE,
            revokedAt = null,
            lastUsedAt = null,
        )

    private fun revokeActiveKeyIfExists(partnerId: Long) {
        val activeKey = partnerApiKeyRepository.findActiveByPartnerId(partnerId) ?: return
        activeKey.revoke()
        partnerApiKeyRepository.save(activeKey)
    }

    private fun validateApiKey(apiKey: PartnerApiKey, plainKey: String) {
        if (!apiKey.verify(plainKey, apiKeyGenerator)) {
            throw UnauthorizedException(INVALID_KEY_MESSAGE)
        }
        if (!apiKey.isActive()) {
            throw PartnerApiKeyInactiveException(apiKey.id, apiKey.status)
        }
    }

    private companion object {
        const val KEY_PREFIX = "partner_"
        const val PLACEHOLDER_PREFIX = "placeholder_"
        const val INVALID_KEY_MESSAGE = "Invalid API key"
    }
}
