package com.sportsapp.domain.mcp.service

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.mcp.dto.IssueMcpTokenCommand
import com.sportsapp.domain.mcp.entity.McpToken
import com.sportsapp.domain.mcp.entity.McpTokenScope
import com.sportsapp.domain.mcp.exception.McpScopeNotFoundException
import com.sportsapp.domain.mcp.gateway.McpPermissionGateway
import com.sportsapp.domain.mcp.repository.McpTokenCustomRepository
import com.sportsapp.domain.mcp.repository.McpTokenRepository
import com.sportsapp.domain.mcp.repository.McpTokenScopeRepository
import com.sportsapp.domain.mcp.vo.McpScope
import com.sportsapp.domain.mcp.vo.McpTokenVerification
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.Base64

@Service
class McpTokenDomainService(
    private val mcpTokenRepository: McpTokenRepository,
    private val mcpTokenScopeRepository: McpTokenScopeRepository,
    private val mcpTokenCustomRepository: McpTokenCustomRepository,
    private val mcpPermissionGateway: McpPermissionGateway,
    private val passwordEncoder: PasswordEncoder,
) {
    data class IssueResult(val plainToken: String, val token: McpToken)

    private val secureRandom = SecureRandom()

    // 2-step save: ID를 먼저 확보한 뒤 plainToken(mcp_<id>_<random>)으로 hash를 갱신한다.
    // 첫 번째 save에는 ID가 없으므로 임시 placeholder로 저장하고, ID 확보 후 실제 hash로 교체한다.
    private companion object {
        const val PLACEHOLDER_PREFIX = "placeholder_"
    }

    fun issueToken(command: IssueMcpTokenCommand): IssueResult {
        val permissionIds = resolvePermissionIds(command.scopes)
        val randomPart = generateRandomPart()
        val placeholder = "$PLACEHOLDER_PREFIX${randomPart}"
        val mcpToken = mcpTokenRepository.save(
            McpToken.create(
                userId = command.userId,
                name = command.name,
                tokenHash = passwordEncoder.encode(placeholder),
                expiresAt = command.expiresAt,
            ),
        )
        val plainToken = "mcp_${mcpToken.id}_${randomPart}"
        mcpToken.updateTokenHash(passwordEncoder.encode(plainToken))
        mcpTokenRepository.save(mcpToken)
        permissionIds.forEach { permissionId ->
            mcpTokenScopeRepository.save(McpTokenScope.create(mcpToken.id, permissionId))
        }
        return IssueResult(plainToken = plainToken, token = mcpToken)
    }

    fun listMyTokens(userId: Long): List<McpToken> =
        mcpTokenCustomRepository.findActiveByUserId(userId)

    @Transactional
    fun recordUsage(tokenId: Long) {
        val mcpToken = mcpTokenRepository.findById(tokenId)
            ?: throw ResourceNotFoundException("McpToken", tokenId)
        mcpToken.recordUsage()
        mcpTokenRepository.save(mcpToken)
    }

    fun revokeToken(tokenId: Long, requesterId: Long) {
        val mcpToken = mcpTokenRepository.findById(tokenId)
            ?: throw ResourceNotFoundException("McpToken", tokenId)
        mcpToken.requireOwnedBy(requesterId)
        mcpToken.revoke()
        mcpTokenRepository.save(mcpToken)
    }

    /**
     * W1-06a: `McpTokenAuthenticationFilter`(bootstrap, 미수정)가 인라인으로 수행하던
     * "토큰 조회 → 해시 대조 → 활성/만료 확인 → 스코프 해석"을 platform의 UseCase로 승격한 계약.
     * 예외가 아니라 [McpTokenVerification] 결과값으로 반환한다 — 실패 사유(존재하지 않음·해시
     * 불일치·비활성·만료)는 구분하지 않고 전부 `invalid()`로 수렴시킨다(필터의 무누출 401과 동일 경계).
     */
    // ReturnCount 억제 근거(W1-DEBT-01): guard clause(early return) 다수 사용의 부산물이다.
    // private-be-code-convention 이 guard clause 를 권장하므로, 중첩 if 로 바꾸는 것이 오히려 규약 위반이다.
    @Suppress("ReturnCount")
    fun verifyToken(plainToken: String): McpTokenVerification {
        val tokenId = McpToken.parseTokenId(plainToken) ?: return McpTokenVerification.invalid()
        val mcpToken = mcpTokenRepository.findById(tokenId) ?: return McpTokenVerification.invalid()
        if (!passwordEncoder.matches(plainToken, mcpToken.tokenHash)) return McpTokenVerification.invalid()
        if (!mcpToken.isUsable()) return McpTokenVerification.invalid()
        return McpTokenVerification.valid(
            tokenId = mcpToken.id,
            userId = mcpToken.userId,
            scopes = resolveScopes(mcpToken.id),
        )
    }

    private fun resolveScopes(tokenId: Long): Set<McpScope> {
        val tokenScopes = mcpTokenScopeRepository.findByTokenId(tokenId)
        val permissionIds = tokenScopes.map { it.permissionId }
        val permissionNames = mcpPermissionGateway.findPermissionNamesBy(permissionIds)
        return tokenScopes.mapNotNull { tokenScope ->
            permissionNames[tokenScope.permissionId]?.let { McpScope.fromPermissionName(it) }
        }.toSet()
    }

    private fun resolvePermissionIds(scopes: List<String>): List<Long> =
        scopes.map { scope ->
            val permissionName = McpScope.of(scope).toPermissionName()
            mcpPermissionGateway.findPermissionIdBy(permissionName)
                ?: throw McpScopeNotFoundException(permissionName)
        }

    private fun generateRandomPart(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
