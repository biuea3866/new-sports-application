package com.sportsapp.domain.common

import java.time.ZonedDateTime
import java.util.UUID

/**
 * 도메인 이벤트 기본 인터페이스.
 *
 * 모든 도메인 이벤트는 이 인터페이스를 구현한다.
 * - [eventId]: 이벤트 고유 식별자 (기본값 UUID, 멱등 처리에 사용)
 * - [occurredAt]: 이벤트 발생 시각 (UTC ZonedDateTime)
 * - [aggregateId]: 이벤트를 발행한 Aggregate 의 ID
 * - [topic]: 이벤트가 발행될 Kafka 토픽 이름 (null 이면 Spring 내부 이벤트로만 발행)
 */
interface DomainEvent {
    val eventId: String
    val occurredAt: ZonedDateTime
    val aggregateId: Long
    val topic: String?
}

/**
 * [DomainEvent] 생성을 돕는 추상 기반 클래스.
 *
 * topic 을 null 로 두면 [SpringDomainEventPublisher] 경로만 사용한다.
 * topic 을 지정하면 [KafkaDomainEventPublisher] 경로로 발행한다.
 */
abstract class AbstractDomainEvent(
    override val aggregateId: Long,
    override val topic: String? = null,
    override val eventId: String = UUID.randomUUID().toString(),
    override val occurredAt: ZonedDateTime = ZonedDateTime.now(java.time.ZoneOffset.UTC),
) : DomainEvent
