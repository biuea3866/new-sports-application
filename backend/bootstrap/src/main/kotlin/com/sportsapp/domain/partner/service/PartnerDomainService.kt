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
import org.springframework.transaction.annotation.Transactional

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

    /**
     * SAGA tx2 — partner 컨텍스트의 로컬 트랜잭션 경계.
     *
     * 내부에서 3회 쓰기(partner 저장 + 키 placeholder 저장 + 실 해시 재저장)가 일어나므로
     * 경계가 없으면 각각 auto-commit 되어, 마지막 쓰기 실패 시 `partners` 1행과 **아무도 쓸 수 없는
     * placeholder 해시 ACTIVE 키 1행**이 영구 잔존한다. 호출부(`CreatePartnerUseCase`)가
     * `@Transactional`을 갖지 않는 SAGA 구조이므로 이 경계는 여기서 소유해야 한다.
     * 기존 `@Transactional` UseCase(재발급·폐기·상태변경)에서 호출되면 REQUIRED 로 정상 참여한다.
     */
    @Transactional
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

    /**
     * 폐기 + 재발급으로 최대 3회 쓴다. 경계가 호출부(`ReissueApiKeyUseCase`)에만 있으면
     * `createPartner` 가 회귀했던 것과 같은 배치가 된다 — 다중 쓰기와 경계가 다른 파일에 있다.
     * REQUIRED 라 `@Transactional` UseCase 에서 호출돼도 동작 변화가 없다.
     */
    @Transactional
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
        if (status == PartnerStatus.ACTIVE) partner.activate() else partner.deactivate()
        partnerRepository.save(partner)
    }

    fun authenticate(keyId: Long, plainKey: String): AuthenticatedPartner {
        val apiKey = partnerApiKeyRepository.findById(keyId) ?: throw UnauthorizedException(INVALID_KEY_MESSAGE)
        validateApiKey(apiKey, plainKey)
        val partner = partnerRepository.findById(apiKey.partnerId) ?: throw PartnerNotFoundException(apiKey.partnerId)
        partner.validateActive()
        return AuthenticatedPartner(partnerId = requireNotNull(partner.id), linkedUserId = partner.linkedUserId)
    }

    /**
     * API Key의 lastUsedAt을 갱신한다. 인증 성공 후 [PartnerApiKeyUsageRecorder] 구현체가
     * 비동기로 호출해 요청 critical path에 쓰기 지연이 끼지 않게 한다.
     */
    fun recordKeyUsage(keyId: Long) {
        val apiKey = partnerApiKeyRepository.findById(keyId)
            ?: throw ResourceNotFoundException("PartnerApiKey", keyId)
        apiKey.recordUsage()
        partnerApiKeyRepository.save(apiKey)
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
