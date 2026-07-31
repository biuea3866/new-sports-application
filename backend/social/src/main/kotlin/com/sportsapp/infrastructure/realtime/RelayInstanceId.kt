package com.sportsapp.infrastructure.realtime

import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 이 JVM(social 인스턴스)의 릴레이 식별자 (W1-07).
 *
 * 자기 발행 메시지 폐기(self-published message suppression, 중복 전달 방지)의 판별 기준이다 —
 * 발신 인스턴스는 로컬 전달과 Redis 발행을 모두 수행하므로, 자기 발행분을 그대로 재구독하면
 * 같은 메시지가 두 번 전달된다. [RealtimeRelaySubscriber]가 수신한 envelope 의
 * `senderInstanceId`와 이 값이 같으면 폐기한다.
 *
 * 빈 생성 시 1회 발급되어 프로세스 생명주기 동안 고정된다(재기동하면 새 값 — 인스턴스 재기동
 * 사이 식별자 연속성은 필요하지 않다, 자기 발행 판별 목적일 뿐이다).
 */
@Component
class RelayInstanceId(
    val value: String = UUID.randomUUID().toString(),
)
