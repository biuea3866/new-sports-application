package com.sportsapp.domain.common

/**
 * 도메인 이벤트 적재를 지원하는 Aggregate Root 기반 클래스.
 *
 * Entity 는 이 클래스를 상속하여 내부에서 [registerEvent] 로 이벤트를 적재한다.
 * DomainService 는 비즈니스 로직 완료 후 [pullDomainEvents] 로 이벤트를 꺼내
 * [DomainEventPublisher.publishAll] 을 호출한다.
 *
 * ```kotlin
 * class Booking(...) : AggregateRoot() {
 *     fun confirm() {
 *         status = BookingStatus.CONFIRMED
 *         registerEvent(BookingEvent.Confirmed(bookingId = id, paymentId = paymentId, recipientUserId = userId))
 *     }
 * }
 *
 * // DomainService
 * val booking = bookingRepository.save(entity)
 * eventPublisher.publishAll(booking.pullDomainEvents())
 * ```
 */
abstract class AggregateRoot {

    @Transient
    private val domainEvents: MutableList<DomainEvent> = mutableListOf()

    /**
     * 발행 대기 이벤트를 등록한다.
     */
    protected fun registerEvent(event: DomainEvent) {
        domainEvents.add(event)
    }

    /**
     * 적재된 이벤트를 반환하고 내부 리스트를 비운다.
     *
     * @return 적재된 이벤트 목록 (변경 불가 복사본)
     */
    fun pullDomainEvents(): List<DomainEvent> {
        val events = domainEvents.toList()
        domainEvents.clear()
        return events
    }
}
