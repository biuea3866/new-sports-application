package com.sportsapp.domain.partner.service

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.common.exceptions.UnauthorizedException
import com.sportsapp.domain.partner.entity.ApiKeyStatus
import com.sportsapp.domain.partner.entity.Partner
import com.sportsapp.domain.partner.entity.PartnerApiKey
import com.sportsapp.domain.partner.entity.PartnerStatus
import com.sportsapp.domain.partner.exception.PartnerApiKeyInactiveException
import com.sportsapp.domain.partner.exception.PartnerNotFoundException
import com.sportsapp.domain.partner.exception.PartnerSuspendedException
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
 * W1-06a: Partner API 키 검증 결과 값 객체. `PartnerApiKeyAuthenticationFilter`(bootstrap, 미수정)와
 * 동일한 무누출 경계를 보존한다 — 오늘 필터가 이미 구분해 노출하는 것(SUSPENDED→403)만 [SUSPENDED]로
 * 남기고, 그 외 실패(키 없음·해시 불일치·키 REVOKED·연동 유저 없음)는 전부 [INVALID]로 수렴시킨다.
 * 새로 추가로 노출하는 구분은 없다 — 존재하지 않는 키와 폐기된 키의 응답이 동일하다.
 */
enum class PartnerApiKeyVerificationFailure { INVALID, SUSPENDED }

data class PartnerApiKeyVerification(
    val valid: Boolean,
    val partnerId: Long?,
    val linkedUserId: Long?,
    val failureReason: PartnerApiKeyVerificationFailure?,
) {
    companion object {
        fun valid(partnerId: Long, linkedUserId: Long): PartnerApiKeyVerification = PartnerApiKeyVerification(
            valid = true,
            partnerId = partnerId,
            linkedUserId = linkedUserId,
            failureReason = null,
        )

        fun invalid(): PartnerApiKeyVerification = PartnerApiKeyVerification(
            valid = false,
            partnerId = null,
            linkedUserId = null,
            failureReason = PartnerApiKeyVerificationFailure.INVALID,
        )

        fun suspended(): PartnerApiKeyVerification = PartnerApiKeyVerification(
            valid = false,
            partnerId = null,
            linkedUserId = null,
            failureReason = PartnerApiKeyVerificationFailure.SUSPENDED,
        )
    }
}

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
// TooManyFunctions 억제 근거(W1-DEBT-01): 이 DomainService 는 타 모듈(bootstrap·edge)과 아키텍처 테스트가
// 타입을 직접 참조한다 — 책임 분리는 크로스 모듈 호출부 갱신을 동반하므로 별도 티켓으로 분리한다
// (같은 정리에서 social 의 무분별한 분리가 bootstrap 테스트 12곳을 깨뜨린 선례가 있다).
@Suppress("TooManyFunctions")
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
     * W1-06a: `PartnerApiKeyAuthenticationFilter`(bootstrap, 미수정)가 이미 위임하던 [authenticate]를
     * 그대로 재사용한다 — 인증 판정 로직을 복사하지 않고, 예외 기반 계약을 결과값 기반으로
     * 감싸기만 한다. keyId 파싱만 [PartnerApiKey.parseKeyId]로 새로 캡슐화한다(필터는 자기 사본을
     * 그대로 유지 — bootstrap 미수정 원칙).
     */
    fun verifyApiKey(plainKey: String): PartnerApiKeyVerification {
        val keyId = PartnerApiKey.parseKeyId(plainKey) ?: return PartnerApiKeyVerification.invalid()
        return try {
            val authenticated = authenticate(keyId, plainKey)
            PartnerApiKeyVerification.valid(
                partnerId = authenticated.partnerId,
                linkedUserId = authenticated.linkedUserId,
            )
        } catch (exception: PartnerSuspendedException) {
            PartnerApiKeyVerification.suspended()
        } catch (exception: BusinessException) {
            PartnerApiKeyVerification.invalid()
        }
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
