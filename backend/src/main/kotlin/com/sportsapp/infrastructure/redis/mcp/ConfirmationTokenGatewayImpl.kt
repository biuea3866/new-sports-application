package com.sportsapp.infrastructure.redis.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenAlreadyConsumedException
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenContext
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenExpiredException
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenGateway
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

/**
 * MCP confirm flow 용 일회용 토큰 Gateway — Redis 구현체.
 *
 * - issue: UUID v4 생성 + Redis SETEX (기본 TTL 5분)
 * - consume: Redis GETDEL (atomic) — 키 없음=만료, 소진 마커 있음=이미 소진
 *
 * 만료 vs 소진 구별:
 *   GETDEL 후 null 이면 "mcp:confirm:consumed:{token}" 키 존재 여부로 판단.
 *   소진 시 consumed 마커를 CONSUMED_MARKER_TTL 동안 보존.
 *
 * 보안 주의: 로그에 토큰 평문 기록 금지. 예외 메시지에는 앞 8자만 표시.
 */
@Component
class ConfirmationTokenGatewayImpl(
    private val stringRedisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : ConfirmationTokenGateway {

    override fun issue(context: ConfirmationTokenContext, ttl: Duration): String {
        val token = UUID.randomUUID().toString()
        val payload = objectMapper.writeValueAsString(context)
        stringRedisTemplate.opsForValue().set(tokenKey(token), payload, ttl)
        return token
    }

    override fun consume(token: String): ConfirmationTokenContext {
        val payload = stringRedisTemplate.opsForValue().getAndDelete(tokenKey(token))
        if (payload != null) {
            stringRedisTemplate.opsForValue().set(consumedMarkerKey(token), "1", CONSUMED_MARKER_TTL)
            return objectMapper.readValue(payload)
        }
        if (stringRedisTemplate.hasKey(consumedMarkerKey(token)) == true) {
            throw ConfirmationTokenAlreadyConsumedException(token)
        }
        throw ConfirmationTokenExpiredException(token)
    }

    private fun tokenKey(token: String): String = "mcp:confirm:$token"

    private fun consumedMarkerKey(token: String): String = "mcp:confirm:consumed:$token"

    companion object {
        private val CONSUMED_MARKER_TTL: Duration = Duration.ofMinutes(10)
    }
}
