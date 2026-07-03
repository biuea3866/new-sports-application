package com.sportsapp.domain.partner.entity

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.partner.gateway.ApiKeyGenerator
import java.time.ZonedDateTime

class PartnerApiKey private constructor(
    val id: Long?,
    val partnerId: Long,
    val keyHash: String,
    initialStatus: ApiKeyStatus,
    initialRevokedAt: ZonedDateTime?,
    initialLastUsedAt: ZonedDateTime?,
) {

    var status: ApiKeyStatus = initialStatus
        private set

    var revokedAt: ZonedDateTime? = initialRevokedAt
        private set

    var lastUsedAt: ZonedDateTime? = initialLastUsedAt
        private set

    fun revoke() {
        if (status == ApiKeyStatus.REVOKED) return
        check(status.canTransitTo(ApiKeyStatus.REVOKED)) {
            "Cannot revoke PartnerApiKey(id=$id): current status=$status"
        }
        status = ApiKeyStatus.REVOKED
        revokedAt = ZonedDateTime.now()
    }

    fun recordUsage() {
        lastUsedAt = ZonedDateTime.now()
    }

    fun isActive(): Boolean = status == ApiKeyStatus.ACTIVE

    /**
     * 평문 키가 이 키의 저장된 해시와 일치하는지 검증한다.
     * keyHash(내부 값)를 외부로 노출하지 않도록 해시 정책([ApiKeyGenerator])을 인자로 받아 내부에서 대조한다.
     */
    fun verify(plainKey: String, apiKeyGenerator: ApiKeyGenerator): Boolean =
        apiKeyGenerator.matches(plainKey, keyHash)

    /**
     * 이 키가 주어진 partner 소유가 아니면 [ResourceNotFoundException]을 던진다(존재 자체를 은닉).
     */
    fun requireOwnedBy(partnerId: Long) {
        if (this.partnerId != partnerId) throw ResourceNotFoundException("PartnerApiKey", id ?: "***")
    }

    companion object {
        fun create(partnerId: Long, keyHash: String): PartnerApiKey {
            require(keyHash.isNotBlank()) { "keyHash must not be blank" }
            return PartnerApiKey(
                id = null,
                partnerId = partnerId,
                keyHash = keyHash,
                initialStatus = ApiKeyStatus.ACTIVE,
                initialRevokedAt = null,
                initialLastUsedAt = null,
            )
        }

        fun reconstitute(
            id: Long,
            partnerId: Long,
            keyHash: String,
            status: ApiKeyStatus,
            revokedAt: ZonedDateTime?,
            lastUsedAt: ZonedDateTime?,
        ): PartnerApiKey = PartnerApiKey(
            id = id,
            partnerId = partnerId,
            keyHash = keyHash,
            initialStatus = status,
            initialRevokedAt = revokedAt,
            initialLastUsedAt = lastUsedAt,
        )
    }
}
