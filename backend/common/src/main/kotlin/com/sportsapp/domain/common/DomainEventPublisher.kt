package com.sportsapp.domain.common

/**
 * 도메인 이벤트 발행 추상화 인터페이스.
 *
 * 구현체:
 * - [KafkaDomainEventPublisher]: 외부 도메인 통신용 Kafka 발행
 * - [SpringDomainEventPublisher]: 모놀리스 내부 트랜잭션 이벤트 (ApplicationEventPublisher)
 * - [RoutingDomainEventPublisher]: topic 유무에 따라 위 두 구현체로 라우팅
 *
 * UseCase 는 어느 경로로 발행되는지 알 필요 없다.
 */
interface DomainEventPublisher {

    /**
     * 단일 이벤트 발행.
     *
     * @param event 발행할 도메인 이벤트
     */
    fun publish(event: DomainEvent)

    /**
     * 복수 이벤트 일괄 발행. Entity.pullDomainEvents() 결과를 그대로 전달한다.
     *
     * @param events 발행할 도메인 이벤트 목록
     */
    fun publishAll(events: List<DomainEvent>) {
        events.forEach { publish(it) }
    }
}
