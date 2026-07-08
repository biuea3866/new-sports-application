package com.sportsapp.infrastructure.partner

import com.sportsapp.domain.partner.gateway.ApiKeyGenerator
import java.security.SecureRandom
import java.util.Base64
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/**
 * API Key 랜덤 문자열 생성·해시 계약 구현.
 *
 * - generateRandomPart: SecureRandom 256-bit(32byte) → Base64 URL-safe(padding 없음).
 *   `domain.mcp.service.McpTokenDomainService`의 randomPart 생성 방식을 준용한다.
 * - hash/matches: PasswordEncoder(BCrypt) 위임.
 */
@Component
class ApiKeyGeneratorImpl(
    private val passwordEncoder: PasswordEncoder,
) : ApiKeyGenerator {

    private val secureRandom = SecureRandom()

    override fun generateRandomPart(): String {
        val bytes = ByteArray(RANDOM_PART_BYTE_LENGTH)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    override fun hash(plainKey: String): String = passwordEncoder.encode(plainKey)

    override fun matches(plainKey: String, keyHash: String): Boolean =
        passwordEncoder.matches(plainKey, keyHash)

    private companion object {
        const val RANDOM_PART_BYTE_LENGTH = 32
    }
}
