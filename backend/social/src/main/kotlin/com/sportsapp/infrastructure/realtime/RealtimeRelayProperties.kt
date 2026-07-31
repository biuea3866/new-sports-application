package com.sportsapp.infrastructure.realtime

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * social 인스턴스 간 STOMP 릴레이(Redis pub/sub) 설정 (W1-07, 아키텍트 §9-3 OQ-7).
 *
 * [enabled]=false(env `CHAT_REALTIME_RELAY_ENABLED`)면 [RealtimeRelayPublisher]가 발행을
 * 건너뛰어 로컬 세션 전달만 수행하는 상태로 완전 복귀한다 — 단일 인스턴스 운영에서는 동작 차이가
 * 0이다(티켓 롤백 절). 값은 부팅 시 고정 바인딩되므로 전환에는 재기동이 필요하다 — 이 값은 빈
 * 등록 자체를 토글하지 않고 [RealtimeRelayPublisher.publish] 매 호출마다 조회되므로,
 * `@ConditionalOnProperty`로 빈 등록을 통째로 가르는 것과 달리 no-conditional-on-property의
 * 취지(런타임 조회 분기)를 만족한다.
 *
 * [channel] — social 전용 네임스페이스 prefix(§3-2 허용 ⑤: "공유 Redis 사용 단 키 네임스페이스를
 * 서비스별로 분리"). 방(room) 단위 채널 대신 **단일 브로드캐스트 채널 + 인스턴스 로컬 필터**를
 * 택했다 — 방 개수는 가변·무제한이라 방 단위 채널은 서비스 성장에 따라 구독 채널 수가 함께
 * 늘어나 Redis pub/sub 채널 관리 비용이 커진다. 단일 채널은 인스턴스 수만큼만 구독하고, 방
 * 라우팅은 payload 의 `roomId` + 로컬 [LocalStompBroadcaster]의 STOMP 목적지(`/topic/rooms/{roomId}`)로
 * 처리한다.
 */
@ConfigurationProperties(prefix = "chat.realtime.relay")
data class RealtimeRelayProperties(
    val enabled: Boolean = true,
    val channel: String = "social:realtime:relay",
)
