package com.sportsapp.domain.booking.service

import com.sportsapp.domain.booking.BookingFeatureFlagKeys
import com.sportsapp.domain.booking.dto.BookingDetail
import com.sportsapp.domain.booking.dto.BookingExpiryCandidate
import com.sportsapp.domain.booking.dto.BookingExpiryFilterResult
import com.sportsapp.domain.booking.dto.BookingExpiryTtlPolicy
import com.sportsapp.domain.booking.dto.BookingOrderItem
import com.sportsapp.domain.booking.dto.BookingResult
import com.sportsapp.domain.booking.dto.FacilityKpiSummary
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.event.BookingEvent
import com.sportsapp.domain.booking.event.BookingRefundRequestedEvent
import com.sportsapp.domain.booking.event.BookingRequestedEvent
import com.sportsapp.domain.booking.exception.InvalidBookingStateException
import com.sportsapp.domain.booking.exception.SlotBusyException
import com.sportsapp.domain.booking.exception.SlotFullException
import com.sportsapp.domain.booking.exception.UnauthorizedBookingAccessException
import com.sportsapp.domain.booking.repository.BookingOrderQueryRepository
import com.sportsapp.domain.booking.repository.BookingRepository
import com.sportsapp.domain.booking.repository.SlotRepository
import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.common.payment.OrderPaymentLiveness
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.ZonedDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

private const val TOP_FACILITY_LIMIT = 5
private val LOCK_TTL = Duration.ofSeconds(10)
private val LOCK_WAIT_TIMEOUT = Duration.ofSeconds(5)
private val LOCK_RETRY_INTERVAL = Duration.ofMillis(50)

@Service
class BookingDomainService(
    private val bookingRepository: BookingRepository,
    private val slotRepository: SlotRepository,
    private val distributedLock: DistributedLock,
    private val domainEventPublisher: DomainEventPublisher,
    private val bookingOrderQueryRepository: BookingOrderQueryRepository,
    private val featureFlagEvaluator: FeatureFlagEvaluator,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun requestBooking(userId: Long, slotId: Long): BookingResult {
        val lockKey = "booking:slot:$slotId"
        val lockValue = "user:$userId"
        if (!spinLock(lockKey, lockValue)) throw SlotBusyException(slotId)
        registerUnlockOnCompletion(lockKey, lockValue)
        return doBooking(userId, slotId, lockKey, lockValue)
    }

    private fun doBooking(userId: Long, slotId: Long, lockKey: String, lockValue: String): BookingResult {
        try {
            val slot = slotRepository.findForUpdateById(slotId)
                ?: throw ResourceNotFoundException("Slot", slotId)
            slot.requireBookable()
            val activeCount = bookingRepository.countBySlotIdAndStatusIn(
                slotId,
                listOf(BookingStatus.PENDING, BookingStatus.CONFIRMED),
            )
            if (activeCount >= slot.capacity) throw SlotFullException(slotId)
            val booking = Booking.createPending(userId, slotId)
            booking.registerEvent(BookingRequestedEvent(bookingId = booking.id, slotId = slotId, userId = userId))
            val saved = bookingRepository.save(booking)
            domainEventPublisher.publishAll(saved.pullDomainEvents())
            return BookingResult(
                bookingId = saved.id,
                slotId = saved.slotId,
                userId = saved.userId,
                status = saved.status,
            )
        } finally {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                distributedLock.unlock(lockKey, lockValue)
            }
        }
    }

    private fun registerUnlockOnCompletion(lockKey: String, lockValue: String) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) return
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCompletion(status: Int) {
                distributedLock.unlock(lockKey, lockValue)
            }
        })
    }

    private fun spinLock(key: String, value: String): Boolean {
        val deadline = System.currentTimeMillis() + LOCK_WAIT_TIMEOUT.toMillis()
        while (System.currentTimeMillis() < deadline) {
            if (distributedLock.tryLock(key, value, LOCK_TTL)) return true
            Thread.sleep(LOCK_RETRY_INTERVAL.toMillis())
        }
        return false
    }

    fun createPendingBooking(userId: Long, slotId: Long): Booking {
        slotRepository.findById(slotId)
            ?: throw ResourceNotFoundException("Slot", slotId)
        val booking = Booking.createPending(
            userId = userId,
            slotId = slotId,
        )
        return bookingRepository.save(booking)
    }

    /**
     * 결제 확정(webhook)에 의한 예약 확정 — [BookingRepository.tryConfirm] CAS(조건부 UPDATE,
     * WHERE status='PENDING')로 전이한다. 비잠금 findById → confirm() → save() 경로는 만료
     * 스위퍼가 먼저 커밋한 EXPIRED를 조건 없는 dirty-checking UPDATE로 덮어쓰는 반대 방향
     * lost update(오버부킹)를 만들 수 있어, tryExpire와 대칭으로 CAS로 닫았다(p2-3).
     * CAS 실패 시 재조회한 현재 상태로 원인을 가른다 — 이미 CONFIRMED면 멱등(webhook 중복),
     * 그 외(EXPIRED 등)면 InvalidBookingStateException을 던져 상태 머신 우회를 막는다.
     *
     * **호출 계약(KDoc contract)**: 이 메서드 호출 **이전에 같은 트랜잭션에서 대상 Booking을
     * 먼저 로드하지 말 것.** `tryConfirm`은 QueryDSL 벌크 UPDATE라 JPA 1차 캐시를 무효화하지
     * 않는다 — 이미 로드된 Booking이 있으면 아래 `findById`가 그 stale 인스턴스를 그대로
     * 반환해 status가 실제 DB 값과 어긋날 수 있다. 현재 유일 호출부(webhook 확정 경로)는
     * 이 계약을 지키고 있다.
     */
    fun confirmBooking(bookingId: Long, paymentId: Long): Booking {
        // named argument 강제(6차 재리뷰 p3) — bookingId/paymentId가 인접한 동일 타입(Long)이라
        // 위치 인자로 바꿔 넘겨도 컴파일이 통과해 다른 예약을 확정시키는 오동작이 조용히
        // 재발할 수 있다.
        val transitioned = bookingRepository.tryConfirm(bookingId = bookingId, paymentId = paymentId)
        val current = bookingRepository.findById(bookingId) ?: throw ResourceNotFoundException("Booking", bookingId)
        if (!transitioned && current.status != BookingStatus.CONFIRMED) {
            throw InvalidBookingStateException(current.status, BookingStatus.CONFIRMED)
        }
        if (transitioned) {
            domainEventPublisher.publishAll(
                listOf(BookingEvent.Confirmed(bookingId = bookingId, paymentId = paymentId, recipientUserId = current.userId)),
            )
        }
        return current
    }

    fun cancelPending(bookingId: Long) {
        val booking = bookingRepository.findById(bookingId)
            ?: throw ResourceNotFoundException("Booking", bookingId)
        if (booking.status == BookingStatus.CANCELLED) return
        booking.cancel()
        bookingRepository.save(booking)
    }

    fun cancel(bookingId: Long, cancelledByUserId: Long, reason: String?): Booking {
        val booking = bookingRepository.findById(bookingId)
            ?: throw ResourceNotFoundException("Booking", bookingId)
        booking.cancel(cancelledByUserId, reason)
        val saved = bookingRepository.save(booking)
        domainEventPublisher.publishAll(saved.pullDomainEvents())
        return saved
    }

    fun findMyBookings(userId: Long, status: BookingStatus?, pageable: Pageable): Page<Booking> =
        bookingRepository.findPageByUserId(userId, status, pageable)

    /**
     * order 통합 조회(BE-08)가 소비하는 사용자별 주문(라벨 title 포함) 조회.
     * orderType=BOOKING 매핑은 order 파사드가 담당한다.
     */
    fun findOrderHistory(userId: Long): List<BookingOrderItem> =
        bookingOrderQueryRepository.findByUserId(userId)

    /**
     * 시설 KPI를 집계합니다.
     *
     * **노쇼율 산정 기준**: 현재 스키마에 NO_SHOW 전용 상태가 없으므로
     * REFUNDED(결제 완료 후 환불된 예약)를 노쇼 proxy로 사용합니다.
     * 향후 NO_SHOW 상태가 추가되면 [countRefundedByOwnerUserIdAndDateRange] 호출을 대체해야 합니다.
     */
    fun aggregateFacilityKpi(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): FacilityKpiSummary {
        val confirmedCount = bookingRepository.countConfirmedByOwnerUserIdAndDateRange(ownerUserId, from, to)
        val totalCapacity = bookingRepository.sumSlotCapacityByOwnerUserIdAndDateRange(ownerUserId, from, to)
        val noShowCount = bookingRepository.countRefundedByOwnerUserIdAndDateRange(ownerUserId, from, to)
        val topFacilityIds = bookingRepository.findTopFacilityIdsByOwnerUserIdAndDateRange(ownerUserId, from, to, TOP_FACILITY_LIMIT)

        val utilizationRate = if (totalCapacity > 0) {
            BigDecimal(confirmedCount).multiply(BigDecimal(100))
                .divide(BigDecimal(totalCapacity), 2, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        val totalBookings = confirmedCount + noShowCount
        val noShowRate = if (totalBookings > 0) {
            BigDecimal(noShowCount).multiply(BigDecimal(100))
                .divide(BigDecimal(totalBookings), 2, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        return FacilityKpiSummary(
            utilizationRate = utilizationRate,
            noShowRate = noShowRate,
            topFacilityIds = topFacilityIds,
        )
    }


    fun getBooking(requesterId: Long, bookingId: Long): Booking {
        val booking = bookingRepository.findById(bookingId)
            ?: throw ResourceNotFoundException("Booking", bookingId)
        if (booking.userId == requesterId) return booking
        // TODO(AUTH-05): Facility.ownerId 조회로 FACILITY_OWNER 권한 분기 추가
        throw UnauthorizedBookingAccessException(bookingId)
    }

    /**
     * 단건 조회(GET /bookings/{id})가 소비하는 booking + slot 조인 상세.
     * Slot도 booking 컨텍스트 자기 aggregate이므로 조인해도 도메인 교차가 아니다.
     */
    fun getBookingDetail(requesterId: Long, bookingId: Long): BookingDetail {
        val booking = getBooking(requesterId, bookingId)
        val slot = slotRepository.findById(booking.slotId)
        return BookingDetail.of(booking, slot)
    }

    /**
     * W1-11c 만료 스위퍼 — PENDING이며 createdAt < (now - ttlMinutes, 빠른 TTL)이고
     * id > afterId(청크 커서)인 예약 후보를 최대 limit건 조회한다. 시간 계산은 이 메서드
     * 내부에서 해결한다(no-time-parameter — 캡슐화 메서드에 시간을 인자로 넘기지 않는다).
     * createdAt을 포함한 [BookingExpiryCandidate]를 반환한다 — [filterExpirable]이 느린 TTL
     * (readyTtlMinutes, live 갈래) 판정과 빠른 TTL(ttlMinutes, attempting 갈래의
     * `max(createdAt, attemptSince)` 재평가) 판정 양쪽에 모두 사용한다.
     *
     * **명명 인자 강제**: `ttlMinutes`(Long)와 `afterId`(Long)가 인접한 동일 타입이라 위치
     * 인자로 바꿔 넘기면 컴파일은 통과하되 TTL↔커서가 뒤바뀌는 오동작이 조용히 재발할 수
     * 있다 — 호출부는 반드시 named argument로 호출한다.
     */
    fun findExpirableBookingCandidates(ttlMinutes: Long, afterId: Long, limit: Int): List<BookingExpiryCandidate> {
        val threshold = ZonedDateTime.now().minusMinutes(ttlMinutes)
        return bookingRepository.findPendingCreatedBefore(threshold, afterId, limit)
    }

    /**
     * W1-11c 6차 재설계 — 만료 후보 중 실제로 만료시킬 대상을 최종 판정한다. payment로부터
     * 받은 orderId별 판정([OrderPaymentLiveness] — domain.common 공유 커널)만으로 판단하므로
     * 도메인 교차가 아니다 — 크로스 컨텍스트 조합 자체(payment 조회 → 값 변환)는
     * application(ExpirePendingBookingsUseCase)이 수행하고, 이 메서드는 booking 자신의
     * 정책(두 TTL)만 적용한다.
     *
     * **앵커 정정(4차 → 5차)**: 4차는 느린 TTL을 `candidate.createdAt`(예약 생성 시각)에
     * 적용했으나, `POST /payments/prepare`가 기존 주문에 새 payment 행을 만드는 가동 중
     * 경로라 예약이 오래전에 생성됐어도 결제가 방금 살아날 수 있다 — 그 경우 예약 생성 시각
     * 기준으로는 다음 주기에 오만료됐다. 앵커를 **payment 발급 시각의 최댓값**([OrderPaymentLiveness.Live.since])으로
     * 옮겨 "결제가 언제 살아났는가"를 정확히 반영한다.
     *
     * **앵커 정정(5차 → 6차, p1)**: 5차는 느린 TTL(live) 갈래만 앵커를 옮겼고, 빠른 TTL
     * 갈래(live가 없는 경우)는 여전히 `candidate.createdAt`(예약 생성 시각)만 보고 즉시 만료를
     * 허용했다. 그런데 `PaymentDomainService.createPending`이 PENDING 행을 먼저 커밋하고 PG
     * 왕복 동안 그 상태로 머무는 창(δ) 안에 스위퍼가 돌면, "방금 시작된 재결제 시도"를
     * 신호 없음으로 오판정해 오만료시킬 수 있었다 — 이 가드가 존재하는 이유 그 자체인 결과
     * ("돈은 받고 예약은 없음")로 이어진다. [OrderPaymentLiveness.Attempting](PENDING 시도
     * 시각)이 있으면 `max(candidate.createdAt, attempting.since)`를 빠른 TTL 앵커로 쓴다.
     *
     * **2차 무력화 재발 방지**: 이 변경은 PENDING에 *면제*를 주는 게 아니라 *시각 앵커*만
     * 준다 — 예약과 같은 트랜잭션에서 함께 생성된 원 payment는 `attemptSince ≈ candidate.createdAt`이라
     * 빠른 TTL에 그대로 걸려 정상 만료된다. 방금 새로 삽입된 재결제 시도만 재-앵커된다.
     *
     * - [OrderPaymentLiveness.Settled]: 돈을 받았다 — 항상 제외(절대 만료 금지). sealed
     *   `when`이 이 분기를 강제하므로 settled 우선 판정을 놓칠 수 없다.
     * - [OrderPaymentLiveness.Live] (8차 — 두 창 모두 검사): `since`가 느린 TTL(readyTtlMinutes)을
     *   아직 지나지 않았으면 결제창에 머무는 사용자로 보아 즉시 제외(단락 평가). 지났다면
     *   `attemptSince`가 없으면 그대로 만료 대상, 있으면 `max(candidate.createdAt, attemptSince)`가
     *   빠른 TTL(ttlMinutes)까지 지나야만 만료 대상 — 둘 중 하나만 지나서는 안 된다(AND).
     * - [OrderPaymentLiveness.Attempting] (live 행이 전혀 없을 때만): `max(candidate.createdAt, since)`가
     *   빠른 TTL(ttlMinutes)을 지나지 않았으면 재결제 시도 중으로 보아 제외, 지났으면 만료 대상.
     * - [OrderPaymentLiveness.None](liveness 맵에 없는 후보도 동일): 빠른 TTL만 재검증 —
     *   candidate.createdAt이 ttlMinutes를 지났으면 만료 대상(F-A 고립 예약 등).
     *
     * **잔여 레이스 창(설계상 한계, p3-1 정정)**: liveness 조회(`PaymentDomainService.findPaymentLiveness`)
     * ~ 이 메서드 호출 사이에 새로 삽입되는 PENDING 행은 이번 청크의 스냅샷에 반영되지
     * 않는다 — 다만 그 창은 "PG 왕복 전체"가 아니라 "청크 처리 수 ms"로 좁혀진다(6차가
     * 좁힌 범위). **이 창에 삽입된 건은 이번 주기에 오만료되며 복구되지 않는다** — 만료는
     * `tryExpire`의 CAS UPDATE로 커밋 완료된 종결 상태라 다음 스케줄 주기가 되돌리지 않는다.
     * "다음 주기 보호"는 **이번 주기에 만료되지 않은 후보**(예: 이 창 이전에 이미 조회에
     * 포함돼 liveness 판정을 받은 candidate)에만 적용되는 별개의 명제다.
     *
     * **8차 재설계 (p1 — 단조성 불변식으로 세 번째 재발을 닫는다)**: 6차·7차는 모두 "live
     * 우선순위" 또는 "최신 앵커 교차 비교"로 live/attempting 중 **승자 하나**를 고르는
     * `classifyOrder`(payment 쪽)에 기대어 이 메서드는 [OrderPaymentLiveness.Live]만 보면
     * 됐다. 그런데 live(느린 TTL)와 attempting(빠른 TTL)은 **서로 다른 TTL**로 소비되므로
     * "더 최신인 쪽"이 "더 짧은 보호 창"일 수 있다 — 승자 하나를 고르는 순간 그 반대편의
     * 보호가 통째로 사라져 방향만 바뀐 오만료가 재발했다. [OrderPaymentLiveness.Live]는
     * 이제 `since`(느린 TTL 앵커)와 `attemptSince`(빠른 TTL 앵커, 있을 때만)를 함께 들고
     * 오므로, 이 메서드가 **두 창을 모두** 검사한다 — **지켜야 할 불변식(단조성): 증거가
     * 추가되면(= `attemptSince`가 채워지면) 보호 창은 절대 짧아지지 않는다.** `since` 조건은
     * `attemptSince` 유무와 무관하게 그대로 유지되므로(AND 결합), 이미 느린 TTL 안에서
     * 보호받던 대상이 attempting 증거가 추가됐다고 풀리는 일은 없다 — 오히려 `attemptSince`가
     * 채워지면 빠른 TTL 창까지 추가로 닫혀야 하므로 보호가 늘어날 수만 있다.
     *
     * **머지 전 하드닝 (리뷰 — 판정을 타입으로 강제)**: 위 8차까지의 AND 결합·단조성 불변식은
     * 전부 이 메서드의 `when` 분기(구 `isExpirable`)에 있었다 — booking(W1-11c)이 이 로직을
     * 검증받는 동안, goods·ticketing·recruitment(W1-11a/b/d)가 각자 이 `when`을 독립적으로
     * 재작성하면 [OrderPaymentLiveness.Live]의 `attemptSince` 검사 한 항을 빠뜨리는 실수가
     * 그대로 재발할 수 있었다(실증: 리뷰 중 `attemptSince` 기본값 `null` 덕에 그 실수가
     * 컴파일 오류 없이 통과했다). 그래서 판정 자체를 [OrderPaymentLiveness.allowsExpiry]로
     * 옮기고, 이 메서드는 그 결과에 위임만 한다 — booking을 포함한 모든 소비 도메인이 같은
     * 단일 판정 지점을 공유하므로 이 결함은 구조적으로 재발할 수 없다.
     */
    fun filterExpirable(
        candidates: List<BookingExpiryCandidate>,
        liveness: Map<Long, OrderPaymentLiveness>,
        ttlPolicy: BookingExpiryTtlPolicy,
    ): BookingExpiryFilterResult {
        val now = ZonedDateTime.now()
        val fastThreshold = now.minusMinutes(ttlPolicy.ttlMinutes)
        val readyThreshold = now.minusMinutes(ttlPolicy.readyTtlMinutes)
        val settled = candidates.filter { liveness[it.bookingId] is OrderPaymentLiveness.Settled }
        val expirableIds = candidates
            .filterNot { liveness[it.bookingId] is OrderPaymentLiveness.Settled }
            .filter { candidate ->
                val candidateLiveness = liveness[candidate.bookingId] ?: OrderPaymentLiveness.None
                candidateLiveness.allowsExpiry(candidate.createdAt, readyThreshold, fastThreshold)
            }
            .map { it.bookingId }
        return BookingExpiryFilterResult(expirableIds = expirableIds, skippedSettledCount = settled.size)
    }

    /**
     * W1-11c 만료 스위퍼 — 청크 단위로 PENDING → EXPIRED CAS 전이한다([BookingRepository.tryExpire]).
     * 슬롯 점유 해제는 별도 보상 로직 없이 이 전이만으로 완료된다(점유는 PENDING/CONFIRMED
     * 카운트로 파생되므로). 트랜잭션 경계는 이 메서드를 호출하는 UseCase(`ExpireBookingChunkUseCase`)가
     * 소유한다 — DomainService는 트랜잭션을 선언하지 않는다.
     */
    fun expireBookings(bookingIds: List<Long>): Int {
        if (bookingIds.isEmpty()) return 0
        return bookingIds.count { bookingRepository.tryExpire(it) }
    }

    /**
     * booking.expiry.enabled 운영 킬 스위치 판정 — 부팅 고정 설정이 아니라 매 스케줄 주기
     * `FeatureFlagEvaluator`로 런타임 조회한다(no-conditional-on-property).
     */
    fun isExpiryEnabled(): Boolean =
        featureFlagEvaluator.isEnabled(BookingFeatureFlagKeys.EXPIRY_ENABLED, FeatureContext.anonymous(), true)

    fun refundBooking(bookingId: Long, callerUserId: Long, refundAmount: BigDecimal, reason: String): Booking {
        val booking = bookingRepository.findById(bookingId)
            ?: throw ResourceNotFoundException("Booking", bookingId)
        booking.requireOwnedBy(callerUserId)
        val paymentId = booking.requireHasPayment()
        booking.refund()
        val saved = bookingRepository.save(booking)
        domainEventPublisher.publishAll(
            listOf(
                BookingRefundRequestedEvent(
                    bookingId = saved.id,
                    paymentId = paymentId,
                    refundAmount = refundAmount,
                    reason = reason,
                ),
            ),
        )
        return saved
    }

}
