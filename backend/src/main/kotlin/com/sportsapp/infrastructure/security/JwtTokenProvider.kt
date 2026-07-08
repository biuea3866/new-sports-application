package com.sportsapp.infrastructure.security

import com.sportsapp.domain.user.gateway.JwtIssuer
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @Value("\${app.jwt.secret}") private val secretValue: String,
) : JwtIssuer {

    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(secretValue.toByteArray(Charsets.UTF_8))
    }

    private val accessTokenExpirySeconds: Long = 30L * 60L

    override fun generateAccessToken(userId: Long, email: String, roles: List<String>): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("roles", roles)
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(accessTokenExpirySeconds)))
            .signWith(secretKey)
            .compact()
    }

    override fun generateRefreshToken(): String = UUID.randomUUID().toString()

    override fun validateToken(token: String): Boolean {
        return try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token)
            true
        } catch (exception: JwtException) {
            false
        } catch (exception: IllegalArgumentException) {
            false
        }
    }

    override fun extractUserId(token: String): Long =
        parseClaims(token).subject.toLong()

    override fun extractEmail(token: String): String =
        requireNotNull(parseClaims(token)["email"] as? String) { "missing email claim in JWT" }

    @Suppress("UNCHECKED_CAST")
    override fun extractRoles(token: String): List<String> =
        requireNotNull((parseClaims(token)["roles"] as? List<*>)?.filterIsInstance<String>()) { "missing roles claim in JWT" }

    override fun extractJti(token: String): String =
        requireNotNull(parseClaims(token).id) { "missing jti claim in JWT" }

    override fun extractExpiration(token: String): Instant =
        requireNotNull(parseClaims(token).expiration) { "missing expiration in JWT" }.toInstant()

    override fun accessTokenExpiresInSeconds(): Long = accessTokenExpirySeconds

    private fun parseClaims(token: String) =
        Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload
}
