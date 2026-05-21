package com.sportsapp.domain.mcp

import com.sportsapp.domain.common.JpaAuditingBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.ZonedDateTime

@Entity
@Table(name = "mcp_tokens")
class McpToken(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "token_hash", nullable = false, unique = true)
    val tokenHash: String,

    initialStatus: McpTokenStatus,
    initialExpiresAt: ZonedDateTime?,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: McpTokenStatus = initialStatus
        private set

    @Column(name = "non_interactive", nullable = false)
    var nonInteractive: Boolean = false
        private set

    @Column(name = "pii_unmask_granted", nullable = false)
    var piiUnmaskGranted: Boolean = false
        private set

    @Column(name = "expires_at")
    var expiresAt: ZonedDateTime? = initialExpiresAt
        private set

    @Column(name = "last_used_at")
    var lastUsedAt: ZonedDateTime? = null
        private set

    fun suspend() {
        check(status.canTransitTo(McpTokenStatus.SUSPENDED)) {
            "Cannot suspend McpToken(id=$id): current status=$status"
        }
        status = McpTokenStatus.SUSPENDED
    }

    fun reactivate() {
        check(status.canTransitTo(McpTokenStatus.ACTIVE)) {
            "Cannot reactivate McpToken(id=$id): current status=$status"
        }
        status = McpTokenStatus.ACTIVE
    }

    fun revoke() {
        check(status.canTransitTo(McpTokenStatus.REVOKED)) {
            "Cannot revoke McpToken(id=$id): current status=$status"
        }
        status = McpTokenStatus.REVOKED
    }

    fun recordUsage() {
        lastUsedAt = ZonedDateTime.now()
    }

    fun requireOwnedBy(userId: Long) {
        if (this.userId != userId) throw McpTokenNotOwnedException(id)
    }

    fun requireActive() {
        if (status != McpTokenStatus.ACTIVE) {
            throw McpTokenInactiveException(id, status)
        }
    }

    fun requireNotExpired() {
        val expiry = expiresAt ?: return
        if (expiry.isBefore(ZonedDateTime.now())) {
            throw McpTokenExpiredException(id, expiry)
        }
    }

    companion object {
        fun create(
            userId: Long,
            name: String,
            tokenHash: String,
            expiresAt: ZonedDateTime?,
        ): McpToken = McpToken(
            userId = userId,
            name = name,
            tokenHash = tokenHash,
            initialStatus = McpTokenStatus.ACTIVE,
            initialExpiresAt = expiresAt,
        )
    }
}
