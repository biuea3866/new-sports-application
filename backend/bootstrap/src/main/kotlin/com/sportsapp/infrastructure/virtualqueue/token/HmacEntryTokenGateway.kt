package com.sportsapp.infrastructure.virtualqueue.token

import com.sportsapp.domain.common.EntryTokenGuard
import com.sportsapp.domain.virtualqueue.gateway.EntryTokenIssuer
import com.sportsapp.domain.virtualqueue.vo.EntryToken
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/** epoch second 기준 시각 — ZonedDateTime만으로 epoch 초 왕복 변환한다(시간 타입 통일 규칙 준수). */
private val EPOCH_SECOND_ORIGIN: ZonedDateTime = ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)

private fun zonedDateTimeOfEpochSecond(epochSecond: Long): ZonedDateTime =
    EPOCH_SECOND_ORIGIN.plusSeconds(epochSecond)

/**
 * `EntryTokenIssuer`(domain.virtualqueue) + `EntryTokenGuard`(domain.common)를 한 클래스로
 * 구현한다 — 서명·검증이 같은 비밀(secret)을 공유하므로 분리하지 않는다(BE-03 티켓).
 *
 * 토큰 형식: `base64url(payload) + "." + base64url(HMAC_SHA256(secret, payload))`.
 * payload = `{targetTypeSlug}|{targetId}|{userId}|{expiresAtEpochSec}` — `targetTypeSlug`는
 * `QueueTargetType.slug`이자 인터셉터가 path variable로 받는 표현과 동일해, [verify]는 별도
 * enum 변환 없이 그 문자열을 그대로 비교한다.
 *
 * [mintStateless]는 Redis를 전혀 건드리지 않는다 — fail-open 경로(BE-04) 전용이다
 * (redis-contract §0-3, `issueIfAbsent`의 `SET NX`가 Redis 장애에서 재실패하는 문제를 회피).
 * [verify]도 HMAC 서명·만료만으로 판정하는 무상태 검증이라 Redis 없이 동작한다.
 */
@Component
class HmacEntryTokenGateway(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${virtual-queue.token.secret}") private val secret: String,
    @Value("\${virtual-queue.token.ttl-seconds:300}") private val ttlSeconds: Long,
) : EntryTokenIssuer, EntryTokenGuard {

    init {
        require(secret.isNotBlank()) {
            "virtual-queue.token.secret must not be blank — 약한 기본값을 두지 않고 부팅 실패로 오배포를 차단한다"
        }
    }

    private val signingKey = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), HMAC_ALGORITHM)

    /**
     * 멱등 발급 — 동일 (target, userId) 재발급 시 기존 토큰을 반환한다(`SET NX EX ttlSeconds`).
     * Redis 의존 — 연결 실패 시 `DataAccessException`이 그대로 전파된다.
     */
    override fun issueIfAbsent(target: QueueTarget, userId: Long): EntryToken {
        val minted = mintStateless(target, userId)
        val markerKey = target.tokenKey(userId)
        val stored = redisTemplate.opsForValue().setIfAbsent(markerKey, minted.raw, Duration.ofSeconds(ttlSeconds))
        if (stored == true) {
            return minted
        }
        val existingRaw = redisTemplate.opsForValue().get(markerKey)
        return existingRaw?.let(::parseToken) ?: minted
    }

    /** fail-open 전용 — HMAC 서명만 수행, Redis 미접근(멱등 마커 생략). */
    override fun mintStateless(target: QueueTarget, userId: Long): EntryToken {
        val expiresAt = ZonedDateTime.now().plusSeconds(ttlSeconds)
        return buildToken(target, userId, expiresAt)
    }

    /**
     * ① 서명 일치 ② 만료 ③ target·user 일치를 전부 확인한다. 무상태(Redis 불요) — 형식이 깨지거나
     * 위조된 토큰은 예외 없이 false를 반환한다(구매 앞단 게이트가 요청마다 호출하는 경로라 방어적).
     */
    override fun verify(targetType: String, targetId: Long, userId: Long, rawToken: String?): Boolean {
        if (rawToken.isNullOrBlank()) return false
        val parsed = parseAndValidateSignature(rawToken) ?: return false
        if (parsed.targetType != targetType) return false
        if (parsed.targetId != targetId) return false
        if (parsed.userId != userId) return false
        return !EntryToken(raw = rawToken, expiresAt = parsed.expiresAt).isExpired()
    }

    private fun buildToken(target: QueueTarget, userId: Long, expiresAt: ZonedDateTime): EntryToken {
        val payload = payloadOf(
            targetTypeSlug = target.type.slug,
            targetId = target.targetId,
            userId = userId,
            expiresAtEpochSec = expiresAt.toEpochSecond(),
        )
        val raw = "${encode(payload.toByteArray(StandardCharsets.UTF_8))}.${encode(sign(payload))}"
        return EntryToken(raw = raw, expiresAt = expiresAt)
    }

    private fun parseToken(raw: String): EntryToken? {
        val parsed = parseAndValidateSignature(raw) ?: return null
        return EntryToken(raw = raw, expiresAt = parsed.expiresAt)
    }

    private fun parseAndValidateSignature(rawToken: String): ParsedPayload? {
        val separatorIndex = rawToken.indexOf('.')
        if (separatorIndex <= 0 || separatorIndex == rawToken.length - 1) return null
        val payloadEncoded = rawToken.substring(0, separatorIndex)
        val signatureEncoded = rawToken.substring(separatorIndex + 1)
        val payloadBytes = decodeOrNull(payloadEncoded) ?: return null
        val signatureBytes = decodeOrNull(signatureEncoded) ?: return null
        val payload = String(payloadBytes, StandardCharsets.UTF_8)
        if (!MessageDigest.isEqual(sign(payload), signatureBytes)) return null
        return ParsedPayload.from(payload)
    }

    private fun sign(payload: String): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(signingKey)
        return mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
    }

    private fun decodeOrNull(value: String): ByteArray? = try {
        Base64.getUrlDecoder().decode(value)
    } catch (cause: IllegalArgumentException) {
        null
    }

    private fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private data class ParsedPayload(
        val targetType: String,
        val targetId: Long,
        val userId: Long,
        val expiresAt: ZonedDateTime,
    ) {
        companion object {
            fun from(payload: String): ParsedPayload? {
                val segments = payload.split(PAYLOAD_DELIMITER)
                if (segments.size != 4) return null
                val targetId = segments[1].toLongOrNull() ?: return null
                val userId = segments[2].toLongOrNull() ?: return null
                val expiresAtEpochSec = segments[3].toLongOrNull() ?: return null
                return ParsedPayload(
                    targetType = segments[0],
                    targetId = targetId,
                    userId = userId,
                    expiresAt = zonedDateTimeOfEpochSecond(expiresAtEpochSec),
                )
            }
        }
    }

    companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val PAYLOAD_DELIMITER = "|"

        private fun payloadOf(targetTypeSlug: String, targetId: Long, userId: Long, expiresAtEpochSec: Long): String =
            listOf(targetTypeSlug, targetId, userId, expiresAtEpochSec).joinToString(PAYLOAD_DELIMITER)
    }
}
