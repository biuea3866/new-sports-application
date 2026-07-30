package com.sportsapp.infrastructure.security

import com.sportsapp.domain.user.gateway.JwtIssuer
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant // private-allow:no-instant — JwtIssuer(domain) 계약이 이미 Instant 반환, 인터페이스 변경은 이 티켓 범위 밖
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * W1-05: JWT 서명·검증 담당 — RS256 비대칭 전환.
 *
 * platform 만 사설키([APP_JWT_PRIVATE_KEY])로 서명하고, 나머지 서비스는 공개키([APP_JWT_PUBLIC_KEY])로
 * 검증만 한다. 사설키가 없는 인스턴스는 발급을 거부하고 검증만 수행한다 (platform 외 서비스의 정상 상태).
 *
 * 무중단 전환을 위해 검증은 항상 RS256 을 우선 시도하고 실패하면 HS256 으로 재시도한다 — 이미 발급된
 * HS256 토큰이 유효기간(액세스 토큰 30분) 동안 계속 유효해야 하기 때문이다. 발급 알고리즘은
 * `app.jwt.algorithm`([APP_JWT_ALGORITHM]) 런타임 설정값으로 전환한다 — 빈 등록 자체를 토글하는
 * `@ConditionalOnProperty`/`@Profile` 은 쓰지 않는다(재기동 없는 전환·즉시 롤백이 목적).
 *
 * 3단계(HS256 검증 제거) 시점 — 기존에 발급된 HS256 토큰이 전부 만료된 뒤(액세스 토큰 최대 수명
 * 경과 후), [parseClaims] 의 HS256 fallback(catch 블록)을 제거하고 RS256 단독 검증으로 전환한다.
 * 이 전환은 코드 제거로 수행하며 릴리즈 노트에 전환 일자를 명시한다.
 */
@Component
class JwtTokenProvider(
    @Value("\${app.jwt.secret}") private val hs256SecretValue: String,
    @Value("\${app.jwt.public-key}") publicKeyPem: String,
    @Value("\${app.jwt.private-key:}") privateKeyPem: String,
    private val environment: Environment,
) : JwtIssuer {

    private val hs256Key: SecretKey by lazy {
        Keys.hmacShaKeyFor(hs256SecretValue.toByteArray(Charsets.UTF_8))
    }

    // 공개키는 부팅 시점에 즉시 파싱한다 — 값이 없거나 형식이 잘못되면 여기서 바로 실패해야
    // "조용한 fallback" 없이 부팅이 실패한다.
    private val rs256PublicKey: PublicKey = parsePublicKey(publicKeyPem)

    // 사설키는 없을 수 있다 (platform 외 서비스의 정상 상태) — 이때 발급은 거부하고 검증만 한다.
    private val rs256PrivateKey: PrivateKey? =
        privateKeyPem.takeIf { pem -> pem.isNotBlank() }?.let { pem -> parsePrivateKey(pem) }

    private val accessTokenExpirySeconds: Long = 30L * 60L

    override fun generateAccessToken(userId: Long, email: String, roles: List<String>): String {
        val now = Instant.now()
        val builder = Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("roles", roles)
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(accessTokenExpirySeconds)))

        // signWith(key) 단일 인자 오버로드는 키 길이에 맞춰 가장 강한 알고리즘을 자동 선택한다 —
        // 우리 HS256 시크릿은 32바이트를 넘는 경우가 많아 그대로 두면 HS512 로 서명돼 버린다.
        // 전환 단계 표(HS256/RS256)가 실제 헤더와 어긋나지 않도록 알고리즘을 명시적으로 고정한다.
        return when (resolveIssueAlgorithm()) {
            JwtSigningAlgorithm.RS256 -> builder.signWith(requirePrivateKeyForIssuance(), Jwts.SIG.RS256).compact()
            JwtSigningAlgorithm.HS256 -> builder.signWith(hs256Key, Jwts.SIG.HS256).compact()
        }
    }

    override fun generateRefreshToken(): String = UUID.randomUUID().toString()

    override fun validateToken(token: String): Boolean =
        try {
            parseClaims(token)
            true
        } catch (exception: JwtException) {
            false
        } catch (exception: IllegalArgumentException) {
            false
        }

    override fun extractUserId(token: String): Long =
        parseClaims(token).subject.toLong()

    override fun extractEmail(token: String): String =
        requireNotNull(parseClaims(token)["email"] as? String) { "missing email claim in JWT" }

    @Suppress("UNCHECKED_CAST")
    override fun extractRoles(token: String): List<String> =
        requireNotNull((parseClaims(token)["roles"] as? List<*>)?.filterIsInstance<String>()) {
            "missing roles claim in JWT"
        }

    override fun extractJti(token: String): String =
        requireNotNull(parseClaims(token).id) { "missing jti claim in JWT" }

    override fun extractExpiration(token: String): Instant = // private-allow:no-instant — JwtIssuer 계약 그대로 구현
        requireNotNull(parseClaims(token).expiration) { "missing expiration in JWT" }.toInstant()

    override fun accessTokenExpiresInSeconds(): Long = accessTokenExpirySeconds

    private fun resolveIssueAlgorithm(): JwtSigningAlgorithm =
        JwtSigningAlgorithm.from(environment.getProperty("app.jwt.algorithm", "HS256"))

    private fun requirePrivateKeyForIssuance(): PrivateKey =
        checkNotNull(rs256PrivateKey) {
            "RS256 발급을 위한 사설키(APP_JWT_PRIVATE_KEY)가 이 인스턴스에 주입되지 않았습니다"
        }

    // 검증은 RS256 을 우선 시도하고 실패하면 HS256 으로 재시도한다 — 무중단 전환 기간의 이중 지원.
    private fun parseClaims(token: String): Claims =
        try {
            Jwts.parser().verifyWith(rs256PublicKey).build().parseSignedClaims(token).payload
        } catch (rs256Failure: JwtException) {
            Jwts.parser().verifyWith(hs256Key).build().parseSignedClaims(token).payload
        }

    companion object {
        private fun parsePublicKey(pem: String): PublicKey {
            check(pem.isNotBlank()) { "JWT 공개키(APP_JWT_PUBLIC_KEY)가 설정되지 않았습니다" }
            val keySpec = X509EncodedKeySpec(decodePemBody(pem))
            return KeyFactory.getInstance("RSA").generatePublic(keySpec)
        }

        private fun parsePrivateKey(pem: String): PrivateKey {
            val keySpec = PKCS8EncodedKeySpec(decodePemBody(pem))
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
        }

        // PEM 헤더/푸터·개행을 제거하고 본문을 base64 디코딩한다. 환경변수로 주입될 때 실제 개행
        // 대신 리터럴 "\n" 문자열로 전달되는 경우까지 함께 처리한다 (compose env 주입 형식).
        private fun decodePemBody(pem: String): ByteArray {
            val body = pem
                .replace("\\n", "\n")
                .lines()
                .filterNot { line -> line.startsWith("-----") }
                .joinToString("")
                .trim()
            return Base64.getDecoder().decode(body)
        }
    }
}

private enum class JwtSigningAlgorithm {
    HS256,
    RS256,
    ;

    companion object {
        fun from(value: String): JwtSigningAlgorithm =
            entries.firstOrNull { candidate -> candidate.name.equals(value.trim(), ignoreCase = true) }
                ?: throw IllegalStateException("지원하지 않는 JWT 발급 알고리즘입니다: $value")
    }
}
