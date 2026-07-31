package com.sportsapp.domain.mcp.entity
import com.sportsapp.domain.mcp.exception.McpTokenExpiredException
import com.sportsapp.domain.mcp.exception.McpTokenInactiveException
import com.sportsapp.domain.mcp.exception.McpTokenNotOwnedException

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

    initialTokenHash: String,

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

    @Column(name = "token_hash", nullable = false, unique = true)
    var tokenHash: String = initialTokenHash
        private set

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

    fun updateTokenHash(newHash: String) {
        require(newHash.isNotBlank()) { "tokenHash must not be blank" }
        tokenHash = newHash
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

    fun isExpired(): Boolean = expiresAt?.isBefore(ZonedDateTime.now()) ?: false

    // 검증(verify) 흐름 전용 질의 메서드 — requireActive()/requireNotExpired()는 예외를 던지므로
    // "예외가 아니라 결과값"이어야 하는 VerifyMcpTokenUseCase 흐름에서는 이 메서드를 쓴다.
    fun isUsable(): Boolean = status == McpTokenStatus.ACTIVE && !isExpired()

    companion object {
        fun create(
            userId: Long,
            name: String,
            tokenHash: String,
            expiresAt: ZonedDateTime?,
        ): McpToken = McpToken(
            userId = userId,
            name = name,
            initialTokenHash = tokenHash,
            initialStatus = McpTokenStatus.ACTIVE,
            initialExpiresAt = expiresAt,
        )

        /**
         * 평문 토큰(`mcp_<id>_<random>`)에서 tokenId를 추출한다. 형식이 아니거나 id 파싱에
         * 실패하면 null (호출부가 pass-through 또는 무효 처리하도록).
         *
         * `McpTokenAuthenticationFilter.parseTokenId`(bootstrap, 미수정)와 동일한 규칙 —
         * 이 메서드가 platform 쪽 정본이다.
         */
        fun parseTokenId(plainToken: String): Long? =
            plainToken.takeIf { it.startsWith("mcp_") }
                ?.removePrefix("mcp_")
                ?.split("_", limit = 2)
                ?.takeIf { it.size >= 2 }
                ?.get(0)
                ?.toLongOrNull()
    }
}
