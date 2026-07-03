package com.sportsapp.domain.partner.entity

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
