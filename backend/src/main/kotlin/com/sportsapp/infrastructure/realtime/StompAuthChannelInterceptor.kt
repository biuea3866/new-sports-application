package com.sportsapp.infrastructure.realtime

import com.sportsapp.domain.user.gateway.JwtIssuer
import java.security.Principal
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Component

private const val BEARER_PREFIX = "Bearer "

/**
 * STOMP CONNECT 프레임의 `Authorization: Bearer <JWT>` 헤더를 검증해 Principal=userId 를 세팅한다.
 *
 * 미인증/무효 토큰은 [BadCredentialsException] 을 던져 CONNECT 를 거부한다
 * ([org.springframework.messaging.simp.stomp.StompSubProtocolHandler] 가 ERROR 프레임을 보내고 세션을 종료).
 */
@Component
class StompAuthChannelInterceptor(
    private val jwtIssuer: JwtIssuer,
) : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
        if (accessor?.command == StompCommand.CONNECT) {
            authenticate(accessor)
        }
        return message
    }

    private fun authenticate(accessor: StompHeaderAccessor) {
        val token = resolveToken(accessor) ?: throw BadCredentialsException("Missing Authorization header")
        if (!jwtIssuer.validateToken(token)) {
            throw BadCredentialsException("Invalid JWT token")
        }
        val userId = jwtIssuer.extractUserId(token)
        accessor.user = StompUserPrincipal(userId)
    }

    private fun resolveToken(accessor: StompHeaderAccessor): String? {
        val header = accessor.getFirstNativeHeader("Authorization") ?: return null
        return header.takeIf { it.startsWith(BEARER_PREFIX) }?.removePrefix(BEARER_PREFIX)
    }

    private class StompUserPrincipal(private val userId: Long) : Principal {
        override fun getName(): String = userId.toString()
    }
}
