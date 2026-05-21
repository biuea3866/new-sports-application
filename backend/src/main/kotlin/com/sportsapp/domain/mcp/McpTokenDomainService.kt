package com.sportsapp.domain.mcp

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.common.PermissionRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64

@Service
class McpTokenDomainService(
    private val mcpTokenRepository: McpTokenRepository,
    private val mcpTokenScopeRepository: McpTokenScopeRepository,
    private val mcpTokenCustomRepository: McpTokenCustomRepository,
    private val permissionRepository: PermissionRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    data class IssueResult(val plainToken: String, val token: McpToken)

    private val secureRandom = SecureRandom()

    fun issueToken(command: IssueMcpTokenCommand): IssueResult {
        val permissionIds = resolvePermissionIds(command.scopes)
        val randomPart = generateRandomPart()
        val placeholder = "placeholder_${randomPart}"
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

    fun revokeToken(tokenId: Long, requesterId: Long) {
        val mcpToken = mcpTokenRepository.findById(tokenId)
            ?: throw ResourceNotFoundException("McpToken", tokenId)
        mcpToken.requireOwnedBy(requesterId)
        mcpToken.revoke()
        mcpTokenRepository.save(mcpToken)
    }

    private fun resolvePermissionIds(scopes: List<String>): List<Long> =
        scopes.map { scope ->
            val permissionName = McpScope.of(scope).toPermissionName()
            permissionRepository.findByName(permissionName)?.id
                ?: throw McpScopeNotFoundException(permissionName)
        }

    private fun generateRandomPart(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
