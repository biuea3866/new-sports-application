package com.sportsapp.domain.ticketing.service

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.common.payment.OrderPaymentLiveness
import com.sportsapp.domain.ticketing.TicketingFeatureFlagKeys
import com.sportsapp.domain.ticketing.dto.EventSalesInfo
import com.sportsapp.domain.ticketing.dto.TicketKpiSummary
import com.sportsapp.domain.ticketing.dto.TicketOrderDetail
import com.sportsapp.domain.ticketing.dto.TicketOrderExpiryCandidate
import com.sportsapp.domain.ticketing.dto.TicketOrderExpiryFilterResult
import com.sportsapp.domain.ticketing.dto.TicketOrderExpiryTtlPolicy
import com.sportsapp.domain.ticketing.dto.TicketOrderResult
import com.sportsapp.domain.ticketing.dto.TicketOrderWithEventTitle
import com.sportsapp.domain.ticketing.dto.TicketSalesSummary
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.EventStatus
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.entity.Seat
import com.sportsapp.domain.ticketing.entity.Ticket
import com.sportsapp.domain.ticketing.entity.TicketOrder
import com.sportsapp.domain.ticketing.event.TicketEvent
import com.sportsapp.domain.ticketing.exception.InvalidOrderStateException
import com.sportsapp.domain.ticketing.exception.LockExpiredException
import com.sportsapp.domain.ticketing.exception.MalformedLockIdException
import com.sportsapp.domain.ticketing.exception.SeatAlreadyLockedException
import com.sportsapp.domain.ticketing.exception.SeatNotLockOwnerException
import com.sportsapp.domain.ticketing.gateway.SeatLockStore
import com.sportsapp.domain.ticketing.dto.EventCriteria
import com.sportsapp.domain.ticketing.repository.EventCustomRepository
import com.sportsapp.domain.ticketing.repository.EventRepository
import com.sportsapp.domain.ticketing.repository.SeatCustomRepository
import com.sportsapp.domain.ticketing.repository.SeatRepository
import com.sportsapp.domain.ticketing.repository.TicketOrderCustomRepository
import com.sportsapp.domain.ticketing.repository.TicketOrderRepository
import com.sportsapp.domain.ticketing.repository.TicketRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.ZonedDateTime

private val SEAT_LOCK_TTL = Duration.ofSeconds(300)
private val logger = LoggerFactory.getLogger(TicketingDomainService::class.java)

@Service
class TicketingDomainService(
    private val eventRepository: EventRepository,
    private val seatRepository: SeatRepository,
    private val eventCustomRepository: EventCustomRepository,
    private val seatCustomRepository: SeatCustomRepository,
    private val ticketOrderCustomRepository: TicketOrderCustomRepository,
    private val seatLockStore: SeatLockStore,
    private val ticketOrderRepository: TicketOrderRepository,
    private val ticketRepository: TicketRepository,
    private val domainEventPublisher: DomainEventPublisher,
    private val featureFlagEvaluator: FeatureFlagEvaluator,
) {
    fun createEvent(
        title: String,
        venue: String,
        startsAt: ZonedDateTime,
        seats: List<SeatSpec>,
        ownerUserId: Long,
    ): Event {
        Event.validateSeatLimit(seats)
        Event.validateNoDuplicateSeats(seats) { Triple(it.section, it.rowNo, it.seatNo) }
        val event = eventRepository.save(Event.create(title, venue, startsAt, ownerUserId))
        val seatList = seats.map { spec ->
            Seat(
                id = 0L,
                eventId = event.id,
                section = spec.section,
                rowNo = spec.rowNo,
                seatNo = spec.seatNo,
                price = spec.price,
            )
        }
        seatRepository.saveAll(seatList)
        return event
    }

    fun getEvent(eventId: Long): Event =
        eventRepository.findById(eventId)
            ?: throw ResourceNotFoundException("Event", eventId)

    fun getSeats(eventId: Long): List<Seat> = seatRepository.findByEventId(eventId)

    fun getSeatsWithAvailability(eventId: Long): List<Pair<Seat, Boolean>> {
        val seats = seatRepository.findByEventId(eventId)
        return seats.map { seat -> seat to (seatLockStore.getOwner(eventId, seat.id) == null) }
    }

    fun listEvents(criteria: EventCriteria, pageable: Pageable): Page<Event> =
        eventCustomRepository.findByCriteria(criteria, pageable)

    // catalog 통합검색용 — status=OPEN 고정 + keyword 부분 일치. CLOSED/CANCELLED는 결과에서 제외한다.
    fun searchOpenEvents(keyword: String?, pageable: Pageable): Page<Event> =
        listEvents(
            EventCriteria(status = EventStatus.OPEN, startsAtFrom = null, startsAtTo = null, keyword = keyword),
            pageable,
        )

    // order 통합조회용 — TicketOrder에 이벤트명(title)을 조인한 표시용 프로젝션.
    fun listTicketOrdersBy(userId: Long): List<TicketOrderWithEventTitle> =
        ticketOrderCustomRepository.findBy(userId)

    fun tryLockSeats(eventId: Long, seatIds: List<Long>, userId: Long): String {
        val lockedSeatIds = mutableListOf<Long>()
        for (seatId in seatIds) {
            val acquired = seatLockStore.tryLock(eventId, seatId, userId, SEAT_LOCK_TTL)
            if (!acquired) {
                lockedSeatIds.forEach { releasedId ->
                    seatLockStore.unlock(eventId, releasedId, userId)
                }
                throw SeatAlreadyLockedException(eventId, seatId)
            }
            lockedSeatIds.add(seatId)
        }
        return seatIds.joinToString(",") { "$eventId:$it" }
    }

    fun releaseSeats(eventId: Long, seatIds: List<Long>, userId: Long) {
        for (seatId in seatIds) {
            val released = seatLockStore.unlock(eventId, seatId, userId)
            if (!released) throw SeatNotLockOwnerException(eventId, seatId)
        }
    }

    fun verifyLockOwner(lockId: String, userId: Long) {
        parseLockId(lockId).forEach { (eventId, seatId) ->
            val owner = seatLockStore.getOwner(eventId, seatId)
                ?: throw LockExpiredException(eventId, seatId)
            if (owner != userId) throw SeatNotLockOwnerException(eventId, seatId)
        }
    }

    fun getTicketOrder(ticketOrderId: Long): TicketOrder =
        ticketOrderRepository.findById(ticketOrderId)
            ?: throw ResourceNotFoundException("TicketOrder", ticketOrderId)

    // 단건 상세 조회용 — TicketOrder에 이벤트명·이벤트id를 조합한 표시용 프로젝션.
    // 참조 Event가 없거나 삭제된 경우 eventTitle은 빈 문자열로 방어한다 (listTicketOrdersBy와 동일 정책).
    fun getTicketOrderDetail(ticketOrderId: Long, requesterId: Long): TicketOrderDetail {
        val order = getTicketOrder(ticketOrderId)
        order.requireOwnedBy(requesterId)
        val event = eventRepository.findById(order.lockedEventId)
        return TicketOrderDetail(
            ticketOrderId = order.id,
            status = order.status,
            eventId = order.lockedEventId,
            eventTitle = event?.title.orEmpty(),
            paymentId = order.paymentId,
            createdAt = order.createdAt,
        )
    }

    @Transactional
    fun createPendingOrder(lockId: String, userId: Long): TicketOrderResult {
        val pairs = parseLockId(lockId)
        val eventId = pairs.first().first
        val seatIds = pairs.map { it.second }
        val order = TicketOrder.create(
            userId = userId,
            lockedEventId = eventId,
            lockedSeatIds = seatIds,
        )
        val saved = ticketOrderRepository.save(order)
        return TicketOrderResult.of(saved)
    }

    /**
     * 결제 확정(webhook) — [TicketOrderRepository.tryConfirm] CAS(조건부 UPDATE, WHERE
     * status='PENDING')로 전이한다(W1-11b 요건 2, 반대 방향 CAS). 비잠금 findById → confirm()
     * → save() 경로는 만료 스위퍼([expireTicketOrders])가 먼저 커밋한 CANCELLED를 조건 없는
     * dirty-checking UPDATE로 덮어쓰는 반대 방향 lost update(같은 좌석 이중 발권)를 만들 수
     * 있어, booking(W1-11c)의 `confirmBooking`과 대칭으로 CAS로 닫는다. CAS 실패 시 재조회한
     * 현재 상태로 원인을 가른다 — 이미 CONFIRMED면 멱등(webhook 중복), 그 외(CANCELLED 등)면
     * InvalidOrderStateException을 던져 상태 머신 우회를 막는다.
     *
     * **호출 계약(KDoc contract)**: 이 메서드 호출 이전에 같은 트랜잭션에서 대상 TicketOrder를
     * 먼저 로드하지 말 것. `tryConfirm`은 QueryDSL 벌크 UPDATE라 JPA 1차 캐시를 무효화하지
     * 않는다 — 이미 로드된 TicketOrder가 있으면 아래 `findById`가 그 stale 인스턴스를 그대로
     * 반환해 status가 실제 DB 값과 어긋날 수 있다. 현재 유일 호출부(webhook 확정 경로)는 이
     * 계약을 지키고 있다.
     *
     * **티켓 발급**: CAS는 상태·paymentId만 원자적으로 바꾸므로, entity의 `confirm()`이 하던
     * 티켓 발급(Ticket.issue)은 CAS 성공 시 이 메서드가 별도로 수행한다 — CAS 실패(이미
     * CONFIRMED)면 중복 발급을 피하기 위해 재발급하지 않는다.
     */
    fun confirmOrder(orderId: Long, paymentId: Long): TicketOrderResult {
        // named argument 강제 — orderId/paymentId가 인접한 동일 타입(Long)이라 위치 인자로
        // 바꿔 넘겨도 컴파일이 통과해 다른 주문을 확정시키는 오동작이 조용히 재발할 수 있다.
        val transitioned = ticketOrderRepository.tryConfirm(orderId = orderId, paymentId = paymentId)
        val current = ticketOrderRepository.findById(orderId) ?: throw ResourceNotFoundException("TicketOrder", orderId)
        if (!transitioned && current.status != OrderStatus.CONFIRMED) {
            throw InvalidOrderStateException("Cannot transit from ${current.status} to ${OrderStatus.CONFIRMED}")
        }
        if (transitioned) {
            val issued = current.lockedSeatIds.map { seatId -> Ticket.issue(ticketOrder = current, seatId = seatId) }
            ticketRepository.saveAll(issued)
            val event = eventRepository.findById(current.lockedEventId)
                ?: throw ResourceNotFoundException("Event", current.lockedEventId)
            domainEventPublisher.publish(
                TicketEvent.Issued(
                    ticketOrderId = current.id,
                    recipientUserId = current.userId,
                    eventTitle = event.title,
                )
            )
        }
        return TicketOrderResult.of(current)
    }

    @Transactional
    fun cancelOrder(orderId: Long) {
        val order = ticketOrderRepository.findById(orderId)
            ?: throw ResourceNotFoundException("TicketOrder", orderId)
        if (order.status == OrderStatus.CANCELLED) return
        order.cancel()
        ticketOrderRepository.save(order)
        val tickets = ticketRepository.findByTicketOrderId(orderId)
        tickets.forEach { ticket ->
            ticket.revoke()
            ticket.softDelete(null)
        }
        if (tickets.isNotEmpty()) ticketRepository.saveAll(tickets)
        registerSeatUnlockAfterCommit(order)
    }

    private fun registerSeatUnlockAfterCommit(order: TicketOrder) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            unlockSeats(order)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                unlockSeats(order)
            }
        })
    }

    private fun unlockSeats(order: TicketOrder) {
        order.lockedSeatIds.forEach { seatId ->
            runCatching { seatLockStore.unlock(order.lockedEventId, seatId, order.userId) }
                .onFailure { logger.warn("Failed to unlock seat $seatId for event ${order.lockedEventId}: ${it.message}") }
        }
    }

    /**
     * W1-11b 만료 스위퍼 — PENDING이며 createdAt < (now - ttlMinutes, 빠른 TTL)이고
     * id > afterId(청크 커서)인 티켓 주문 후보를 최대 limit건 조회한다. 시간 계산은 이 메서드
     * 내부에서 해결한다(no-time-parameter). booking(W1-11c)의
     * [com.sportsapp.domain.booking.service.BookingDomainService.findExpirableBookingCandidates]와
     * 동일한 구조 — createdAt을 포함한 [TicketOrderExpiryCandidate]를 반환하고,
     * [filterExpirableTicketOrders]가 느린 TTL·빠른 TTL 판정 양쪽에 이 값을 앵커로 쓴다.
     *
     * **명명 인자 강제**: `ttlMinutes`(Long)와 `afterId`(Long)가 인접한 동일 타입이라 위치
     * 인자로 바꿔 넘기면 컴파일은 통과하되 TTL↔커서가 뒤바뀌는 오동작이 조용히 재발할 수
     * 있다 — 호출부는 반드시 named argument로 호출한다.
     */
    fun findExpirableTicketOrderCandidates(ttlMinutes: Long, afterId: Long, limit: Int): List<TicketOrderExpiryCandidate> {
        val threshold = ZonedDateTime.now().minusMinutes(ttlMinutes)
        return ticketOrderRepository.findPendingCreatedBefore(threshold, afterId, limit)
    }

    /**
     * W1-11b 만료 스위퍼 — 만료 후보 중 실제로 만료시킬 대상을 최종 판정한다. payment로부터
     * 받은 orderId별 판정([OrderPaymentLiveness] — domain.common 공유 커널)만으로 판단하므로
     * 도메인 교차가 아니다. 크로스 컨텍스트 조합 자체(payment 조회 → 값 변환)는
     * application([com.sportsapp.application.ticketing.usecase.ExpirePendingTicketOrdersUseCase])이
     * 수행하고, 이 메서드는 ticketing 자신의 정책(두 TTL)만 적용한다.
     *
     * **판정을 재구현하지 않는다** — booking(W1-11c)이 8차 재설계까지 겪은 "단조성 불변식
     * 누락"(live/attempting 두 창 중 한 항을 빠뜨리는 실수)이 여기서 재발하지 않도록,
     * settled 우선 판정 이후의 AND 결합·단조성 계산은 전부
     * [OrderPaymentLiveness.allowsExpiry]에 위임한다. 이 메서드는 settled 분기(항상 제외)만
     * sealed `when` 없이 필터로 선처리하고, 나머지는 `allowsExpiry` 호출 하나로 끝난다.
     */
    fun filterExpirableTicketOrders(
        candidates: List<TicketOrderExpiryCandidate>,
        liveness: Map<Long, OrderPaymentLiveness>,
        ttlPolicy: TicketOrderExpiryTtlPolicy,
    ): TicketOrderExpiryFilterResult {
        val now = ZonedDateTime.now()
        val fastThreshold = now.minusMinutes(ttlPolicy.ttlMinutes)
        val readyThreshold = now.minusMinutes(ttlPolicy.readyTtlMinutes)
        val settled = candidates.filter { liveness[it.orderId] is OrderPaymentLiveness.Settled }
        val expirableIds = candidates
            .filterNot { liveness[it.orderId] is OrderPaymentLiveness.Settled }
            .filter { candidate ->
                val candidateLiveness = liveness[candidate.orderId] ?: OrderPaymentLiveness.None
                candidateLiveness.allowsExpiry(candidate.createdAt, readyThreshold, fastThreshold)
            }
            .map { it.orderId }
        return TicketOrderExpiryFilterResult(expirableIds = expirableIds, skippedSettledCount = settled.size)
    }

    /**
     * W1-11b 만료 스위퍼 — 청크 단위로 PENDING → CANCELLED CAS 전이한다
     * ([TicketOrderRepository.tryExpire]). 신규 상태(EXPIRED)를 추가하지 않고 기존
     * CANCELLED로 전이한다 — 만료 여부는 지표·로그로만 구분한다(호출부
     * [com.sportsapp.presentation.ticketing.scheduler.TicketOrderExpiryScheduler]가 계측).
     *
     * **좌석 락 해제**: ticketing의 PENDING 주문은 좌석 배타성을 DB가 아니라 Redis 좌석 락
     * (`SeatLockStoreImpl`, TTL 300초)으로 유지한다 — 이 TTL(5분)은 스위퍼 TTL(15분)보다
     * 훨씬 먼저 자연 만료되므로 대부분 이미 해제된 상태지만, 락이 갱신되는 등 예외적으로
     * 남아있을 가능성을 방어하기 위해 CAS 성공 건에 한해 기존 [cancelOrder]와 동일한 해제
     * 경로([registerSeatUnlockAfterCommit])를 재사용한다. PENDING 주문에는 아직 Ticket이
     * 발급되지 않으므로(발급은 [confirmOrder] 시점) 티켓 회수는 필요 없다 — 새 보상 로직을
     * 만들지 않는다.
     *
     * 트랜잭션 경계는 이 메서드를 호출하는 UseCase
     * ([com.sportsapp.application.ticketing.usecase.ExpireTicketOrderChunkUseCase])가 소유한다
     * — DomainService는 트랜잭션을 선언하지 않는다.
     */
    fun expireTicketOrders(orderIds: List<Long>): Int {
        if (orderIds.isEmpty()) return 0
        return orderIds.count { orderId ->
            val transitioned = ticketOrderRepository.tryExpire(orderId)
            if (transitioned) {
                ticketOrderRepository.findById(orderId)?.let { registerSeatUnlockAfterCommit(it) }
            }
            transitioned
        }
    }

    /**
     * ticketing.expiry.enabled 운영 킬 스위치 판정 — 부팅 고정 설정이 아니라 매 스케줄 주기
     * `FeatureFlagEvaluator`로 런타임 조회한다(no-conditional-on-property).
     */
    fun isExpiryEnabled(): Boolean =
        featureFlagEvaluator.isEnabled(TicketingFeatureFlagKeys.EXPIRY_ENABLED, FeatureContext.anonymous(), true)

    fun calculateAmount(lockId: String): BigDecimal {
        val pairs = parseLockId(lockId)
        val eventId = pairs.first().first
        val seatIds = pairs.map { it.second }
        val seats = seatRepository.findByEventId(eventId)
        return seats.filter { it.id in seatIds }.sumOf { it.price }
    }

    fun countEventsByOwnerIdGroupByStatus(ownerId: Long): Map<EventStatus, Long> =
        eventRepository.countByOwnerIdGroupByStatus(ownerId)

    fun sumTotalSeatsByOwnerId(ownerId: Long): Long =
        seatCustomRepository.sumTotalSeatsByOwnerId(ownerId)

    fun sumSoldSeatsByOwnerId(ownerId: Long): Long =
        seatCustomRepository.sumSoldSeatsByOwnerId(ownerId)

    fun aggregateTicketSales(
        ownerUserId: Long,
        eventId: Long?,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): TicketSalesSummary =
        ticketOrderCustomRepository.aggregateTicketSales(ownerUserId, eventId, from, to)

    fun findEventsByOwnerId(ownerId: Long, pageable: Pageable, status: EventStatus?): Page<Event> =
        eventRepository.findByOwnerId(ownerId, status, pageable)

    fun getEventSalesInfo(eventId: Long): EventSalesInfo {
        val event = eventRepository.findById(eventId)
            ?: throw ResourceNotFoundException("Event", eventId)
        val seats = seatRepository.findByEventId(eventId)
        val soldCount = seatCustomRepository.countSoldByEventId(eventId)
        return EventSalesInfo(event = event, seats = seats, soldCount = soldCount)
    }

    fun openEvent(eventId: Long) {
        val event = eventRepository.findById(eventId)
            ?: throw ResourceNotFoundException("Event", eventId)
        event.openSales()
        eventRepository.save(event)
    }

    fun closeEvent(eventId: Long) {
        val event = eventRepository.findById(eventId)
            ?: throw ResourceNotFoundException("Event", eventId)
        event.close()
        eventRepository.save(event)
    }

    fun deleteEvent(eventId: Long, deletedBy: Long) {
        val event = eventRepository.findById(eventId)
            ?: throw ResourceNotFoundException("Event", eventId)
        event.requireDeletable()
        eventRepository.softDelete(eventId, deletedBy)
        seatRepository.softDeleteByEventId(eventId, deletedBy)
    }

    fun issueComplimentary(eventId: Long, seatId: Long, operatorUserId: Long): Ticket {
        val event = eventRepository.findById(eventId)
            ?: throw ResourceNotFoundException("Event", eventId)
        event.requireOwnedBy(operatorUserId)
        val ticket = Ticket.issueComplimentary(seatId)
        return ticketRepository.save(ticket)
    }

    fun aggregateTicketKpi(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): TicketKpiSummary {
        val summary = ticketOrderCustomRepository.aggregateTicketSales(ownerUserId, null, from, to)
        val complimentaryCount = ticketOrderCustomRepository.countComplimentaryByOwnerUserIdAndDateRange(ownerUserId, from, to)

        val totalCount = summary.totalTicketCount + summary.cancelledCount
        val refundRate = if (totalCount > 0) {
            BigDecimal(summary.cancelledCount).multiply(BigDecimal(100))
                .divide(BigDecimal(totalCount), 2, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        return TicketKpiSummary(
            totalSoldCount = summary.totalTicketCount,
            refundRate = refundRate,
            complimentaryCount = complimentaryCount,
        )
    }

    private fun parseLockId(lockId: String): List<Pair<Long, Long>> =
        lockId.split(",").map { token ->
            val parts = token.split(":")
            if (parts.size != 2) throw MalformedLockIdException(lockId)
            val eventId = parts[0].toLongOrNull() ?: throw MalformedLockIdException(lockId)
            val seatId = parts[1].toLongOrNull() ?: throw MalformedLockIdException(lockId)
            eventId to seatId
        }
}

data class SeatSpec(
    val section: String,
    val rowNo: String,
    val seatNo: String,
    val price: BigDecimal,
)
