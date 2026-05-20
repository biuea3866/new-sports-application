package com.sportsapp.infrastructure.security

import com.sportsapp.domain.user.JwtIssuer
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
        parseClaims(token)["email"] as? String ?: ""

    @Suppress("UNCHECKED_CAST")
    override fun extractRoles(token: String): List<String> =
        (parseClaims(token)["roles"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

    override fun accessTokenExpiresInSeconds(): Long = accessTokenExpirySeconds

    private fun parseClaims(token: String) =
        Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).payload
}
