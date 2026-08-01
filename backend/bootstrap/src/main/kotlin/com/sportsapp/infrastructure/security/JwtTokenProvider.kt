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
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * W1-05: JWT 서명·검증 담당 — RS256 비대칭 전환.
 *
 * platform 만 사설키([APP_JWT_PRIVATE_KEY])로 서명하고, 나머지 서비스는 공개키([APP_JWT_PUBLIC_KEY])로
 * 검증만 한다. 사설키가 없는 인스턴스는 발급을 거부하고 검증만 수행한다 (platform 외 서비스의 정상 상태).
 * **공개키도 없는 인스턴스**(RSA 키를 아직 프로비저닝하지 않은 1단계 배포)는 RS256 검증 자체를
 * 시도하지 않고 HS256 검증만 수행한다 — 키 미주입이 곧 부팅 실패가 되지 않는다. "조용한 폴백 금지"는
 * "RS256으로 서명해 놓고 검증에 실패하면 조용히 HS256으로 넘어간다"는 의미이지, "RS256이 아직
 * 구성되지 않은 상태"를 오류로 취급하라는 뜻이 아니다 — 후자는 1단계 배포의 정상 상태다.
 *
 * 무중단 전환을 위해 검증은 (공개키가 주입돼 있으면) 항상 RS256 을 우선 시도하고 실패하면 HS256 으로
 * 재시도한다 — 이미 발급된 HS256 토큰이 유효기간(액세스 토큰 30분) 동안 계속 유효해야 하기 때문이다.
 * 발급 알고리즘은 `app.jwt.algorithm`([APP_JWT_ALGORITHM]) 설정값으로 전환한다 — 빈 등록 자체를
 * 토글하는 `@ConditionalOnProperty`/`@Profile` 은 쓰지 않는다(부팅 시 빈 구성 자체는 항상 동일하게
 * 유지하고, 알고리즘 분기는 매 호출 시 런타임 조회로 한다 — 값을 필드에 캐싱하지 않는다).
 *
 * **주의 — 이 배포 형태(docker compose, config server·`@RefreshScope` 미도입)에서
 * `APP_JWT_ALGORITHM` 값 변경은 컨테이너 재기동이 필요하다.** OS 환경변수는 프로세스 시작 시점에
 * 고정되므로 "재기동 없는 즉시 전환"은 지원하지 않는다. [resolveIssueAlgorithm] 이 필드 캐싱 대신
 * 매 호출마다 [Environment.getProperty] 를 다시 읽는 이유는 "재기동 후 새 값이 곧바로 반영되도록"
 * 보장하기 위함이지(캐싱했다면 재기동해도 정적 초기화 시점 값이 굳어버릴 위험이 있다), 프로세스가
 * 떠 있는 도중 값이 바뀐다는 뜻이 아니다. 재기동 없는 전환이 실제로 필요해지면 레포에 이미 있는
 * `FeatureFlagEvaluator`(Redis/MySQL 백엔드, `VirtualQueueFeatureFlagKeys` 선례)로 교체를 검토한다 —
 * 이 티켓에서는 채택하지 않았다: 서명 알고리즘은 (a) platform 단일 인스턴스만 이슈 경로를 타고
 * (b) 1→2→3단계로 드물게만 전환되는 값이라, 인증 발급 경로에 Redis/MySQL 의존성을 새로 얹는
 * 트레이드오프가 변경 빈도 대비 과하다고 판단했다.
 *
 * `APP_JWT_ALGORITHM=RS256` 으로 설정됐는데 사설키가 없으면 **부팅 시점에 실패**한다(조용한 HS256
 * 폴백 금지) — 아래 `init` 블록 참고.
 *
 * 3단계(HS256 검증 제거) 시점 — 기존에 발급된 HS256 토큰이 전부 만료된 뒤(액세스 토큰 최대 수명
 * 경과 후), [parseClaims] 의 HS256 fallback(catch 블록)을 제거하고 RS256 단독 검증으로 전환한다.
 * 이 전환은 코드 제거로 수행하며 릴리즈 노트에 전환 일자를 명시한다.
 */
@Component
// TooManyFunctions 억제 근거(W1-DEBT-01): JwtIssuer(domain gateway) 계약의 구현체로, 메서드 수가 곧 계약
// 크기다. 쪼개면 하나의 인터페이스 구현이 여러 클래스로 흩어져 계약-구현 대응이 깨진다.
@Suppress("TooManyFunctions")
class JwtTokenProvider(
    @Value("\${app.jwt.secret}") private val hmacSecretValue: String,
    @Value("\${app.jwt.public-key:}") publicKeyPem: String,
    @Value("\${app.jwt.private-key:}") privateKeyPem: String,
    private val environment: Environment,
) : JwtIssuer {

    // HS256 고정 — signWith(key) 단일 인자 오버로드는 키 길이에 맞춰 가장 강한 알고리즘을 자동
    // 선택해(32바이트 이상이면 HS384, 48바이트 이상이면 HS512) 실제 헤더가 HS256이 아니게 된다.
    // 반대로 HS512를 못박으면 시크릿이 64바이트 미만인 환경(예: 짧은 로컬 시크릿)에서 서명 자체가
    // 실패해 로그인이 전면 불가해진다 — 어떤 시크릿 길이에서도 성립하는 HS256을 고정값으로 쓴다.
    private val hmacKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(hmacSecretValue.toByteArray(Charsets.UTF_8))
    }

    // 공개키는 없을 수 있다 (RSA 키를 아직 프로비저닝하지 않은 1단계 배포의 정상 상태) — 이때
    // RS256 검증은 아예 시도하지 않고 HS256 검증만 수행한다. 값이 있는데 형식이 잘못됐을 때만
    // 여기서 즉시 부팅 실패한다(조용한 폴백 금지는 "잘못된 값을 무시"하는 경우에 적용된다).
    private val rs256PublicKey: PublicKey? =
        publicKeyPem.takeIf { pem -> pem.isNotBlank() }?.let { pem -> parsePublicKey(pem) }

    // 사설키는 없을 수 있다 (platform 외 서비스의 정상 상태) — 이때 발급은 거부하고 검증만 한다.
    private val rs256PrivateKey: PrivateKey? =
        privateKeyPem.takeIf { pem -> pem.isNotBlank() }?.let { pem -> parsePrivateKey(pem) }

    private val accessTokenExpirySeconds: Long = 30L * 60L

    init {
        // APP_JWT_ALGORITHM=RS256 인데 사설키가 없으면 "조용히 HS256으로 계속 발급"하지 않고
        // 부팅 시점에 즉시 실패한다 — 운영자가 RS256 전환을 의도했다면 그 실수를 배포 직후에 알아야 한다.
        if (resolveIssueAlgorithm() == JwtSigningAlgorithm.RS256) {
            checkNotNull(rs256PrivateKey) {
                "APP_JWT_ALGORITHM=RS256 로 설정됐지만 발급용 사설키(APP_JWT_PRIVATE_KEY)가 이 인스턴스에 주입되지 않았습니다"
            }
        }
    }

    override fun generateAccessToken(userId: Long, email: String, roles: List<String>): String {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val builder = Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("roles", roles)
            .id(UUID.randomUUID().toString())
            // JWT 규격이 epoch 초를 요구하는 경계 — 변환은 이 어댑터 안에서만 한다 (W1-DEBT-01).
            .issuedAt(Date.from(now.toInstant()))
            .expiration(Date.from(now.plusSeconds(accessTokenExpirySeconds).toInstant()))

        // signWith(key) 단일 인자 오버로드는 키 길이에 맞춰 가장 강한 알고리즘을 자동 선택한다 —
        // 시크릿이 32바이트를 넘으면 그대로 두었을 때 HS384/HS512 로 서명돼 버린다(실제로 발견된
        // 결함). 전환 단계 표(HS256/RS256)가 실제 헤더와 어긋나지 않도록 알고리즘을 명시적으로 고정한다.
        return when (resolveIssueAlgorithm()) {
            JwtSigningAlgorithm.RS256 -> builder.signWith(requirePrivateKeyForIssuance(), Jwts.SIG.RS256).compact()
            JwtSigningAlgorithm.HS256 -> builder.signWith(hmacKey, Jwts.SIG.HS256).compact()
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

    // JWT 라이브러리가 주는 Date 를 도메인 표준 시간 타입으로 변환해 돌려준다 — 규격 타입이
    // 도메인 계약을 오염시키지 않게 이 경계에서 끊는다 (W1-DEBT-01).
    override fun extractExpiration(token: String): ZonedDateTime =
        requireNotNull(parseClaims(token).expiration) { "missing expiration in JWT" }
            .toInstant()
            .atZone(ZoneOffset.UTC)

    override fun accessTokenExpiresInSeconds(): Long = accessTokenExpirySeconds

    private fun resolveIssueAlgorithm(): JwtSigningAlgorithm =
        JwtSigningAlgorithm.from(environment.getProperty("app.jwt.algorithm", "HS256"))

    private fun requirePrivateKeyForIssuance(): PrivateKey =
        checkNotNull(rs256PrivateKey) {
            "RS256 발급을 위한 사설키(APP_JWT_PRIVATE_KEY)가 이 인스턴스에 주입되지 않았습니다"
        }

    // 공개키가 있으면 RS256 을 우선 시도하고 실패하면 HS256 으로 재시도한다(무중단 전환 기간의 이중
    // 지원). 공개키가 없는 인스턴스(1단계 배포)는 RS256 시도 자체를 건너뛰고 HS256 만 검증한다.
    private fun parseClaims(token: String): Claims {
        val publicKey = rs256PublicKey
        if (publicKey != null) {
            try {
                return Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token).payload
            } catch (rs256Failure: JwtException) {
                // RS256 검증 실패 — 전환기 HS256 토큰일 수 있으므로 아래에서 재시도한다.
            }
        }
        return Jwts.parser().verifyWith(hmacKey).build().parseSignedClaims(token).payload
    }

    companion object {
        private fun parsePublicKey(pem: String): PublicKey {
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
                ?: error("지원하지 않는 JWT 발급 알고리즘입니다: $value")
    }
}
