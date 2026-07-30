package com.sportsapp.infrastructure.realtime

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

private const val REALTIME_ENABLED_PROPERTY = "chat.realtime.enabled"

// TDD 용량 산정(FR-6): 300세션 저빈도 텍스트 기준 clientInboundChannel/clientOutboundChannel core8/max16
private const val CHANNEL_CORE_POOL_SIZE = 8
private const val CHANNEL_MAX_POOL_SIZE = 16

// REST CORS(SecurityConfig.corsConfigurationSource)와 동일한 출처 제한 — "*" 전면 허용 금지
private val ALLOWED_ORIGIN_PATTERNS = arrayOf("http://localhost:*", "http://127.0.0.1:*")

/**
 * WebSocket/STOMP 실시간 전송 계층 (FR-6). `/ws` STOMP endpoint + in-memory Simple Broker.
 *
 * `chat.realtime.enabled=false` 면 빈 자체가 등록되지 않아 `/ws` 가 404, REST 채팅 경로는 무영향
 * (Release Scenario 롤백 지점).
 */
@Configuration
@EnableWebSocketMessageBroker
@ConditionalOnProperty(name = [REALTIME_ENABLED_PROPERTY], havingValue = "true", matchIfMissing = false)
class WebSocketConfig(
    private val stompAuthChannelInterceptor: StompAuthChannelInterceptor,
) : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(*ALLOWED_ORIGIN_PATTERNS)
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic", "/queue")
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(stompAuthChannelInterceptor)
        registration.taskExecutor()
            .corePoolSize(CHANNEL_CORE_POOL_SIZE)
            .maxPoolSize(CHANNEL_MAX_POOL_SIZE)
    }

    override fun configureClientOutboundChannel(registration: ChannelRegistration) {
        registration.taskExecutor()
            .corePoolSize(CHANNEL_CORE_POOL_SIZE)
            .maxPoolSize(CHANNEL_MAX_POOL_SIZE)
    }
}
