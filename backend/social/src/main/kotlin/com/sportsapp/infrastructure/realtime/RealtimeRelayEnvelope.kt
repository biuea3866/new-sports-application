package com.sportsapp.infrastructure.realtime

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.ZonedDateTime

/**
 * social 인스턴스 간 STOMP 릴레이 Redis 채널의 wire 포맷 (W1-07).
 *
 * 이 채널은 Kafka 가 아니라 Redis pub/sub 이라 타입 헤더 개념이 없으므로, 판별 프로퍼티
 * [eventType]으로 다형 역직렬화한다(`PaymentEvent`와 동일 관례 — `ADD_TYPE_INFO_HEADERS=false`
 * Kafka 프로듀서가 payload 판별자만으로 다형 역직렬화하는 것과 같은 이유).
 *
 * [senderInstanceId] — 발행 인스턴스([RelayInstanceId]) 식별자. 구독 측([RealtimeRelaySubscriber])이
 * 자기 발행분을 폐기(자기 발행 메시지 무시 — 중복 전달 방지)하는 데 쓴다.
 * [roomId] — 로컬 전달 시 STOMP 목적지(`/topic/rooms/{roomId}`) 결정에 쓰는 방 식별자(방 격리).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "eventType",
    visible = true,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = RealtimeRelayEnvelope.MessageBroadcast::class, name = RealtimeRelayEnvelope.TYPE_MESSAGE),
    JsonSubTypes.Type(value = RealtimeRelayEnvelope.TypingBroadcast::class, name = RealtimeRelayEnvelope.TYPE_TYPING),
    JsonSubTypes.Type(value = RealtimeRelayEnvelope.ReadBroadcast::class, name = RealtimeRelayEnvelope.TYPE_READ),
)
sealed class RealtimeRelayEnvelope(
    val senderInstanceId: String,
    val roomId: Long,
) {
    abstract val eventType: String

    /** 채팅 메시지 팬아웃 — [com.sportsapp.domain.message.gateway.BroadcastMessage] 대응. */
    class MessageBroadcast(
        senderInstanceId: String,
        roomId: Long,
        val messageId: Long,
        val userId: Long,
        val content: String,
        val createdAt: ZonedDateTime,
    ) : RealtimeRelayEnvelope(senderInstanceId, roomId) {
        override val eventType: String = TYPE_MESSAGE
    }

    /** 타이핑 신호 팬아웃 — [com.sportsapp.domain.message.gateway.TypingEvent] 대응. */
    class TypingBroadcast(
        senderInstanceId: String,
        roomId: Long,
        val userId: Long,
        val typing: Boolean,
    ) : RealtimeRelayEnvelope(senderInstanceId, roomId) {
        override val eventType: String = TYPE_TYPING
    }

    /** 읽음 신호 팬아웃 — [com.sportsapp.domain.message.gateway.ReadEvent] 대응. */
    class ReadBroadcast(
        senderInstanceId: String,
        roomId: Long,
        val userId: Long,
        val lastReadMessageId: Long,
    ) : RealtimeRelayEnvelope(senderInstanceId, roomId) {
        override val eventType: String = TYPE_READ
    }

    companion object {
        const val TYPE_MESSAGE = "MESSAGE"
        const val TYPE_TYPING = "TYPING"
        const val TYPE_READ = "READ"
    }
}
