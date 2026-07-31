package com.sportsapp.domain.booking.service

import com.sportsapp.domain.booking.BookingFeatureFlagKeys
import com.sportsapp.domain.booking.dto.BookingDetail
import com.sportsapp.domain.booking.dto.BookingExpiryCandidate
import com.sportsapp.domain.booking.dto.BookingExpiryFilterResult
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
        val transitioned = bookingRepository.tryConfirm(bookingId, paymentId)
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
     * createdAt을 포함한 [BookingExpiryCandidate]를 반환한다 — [filterExpirable]이 느린
     * TTL(readyTtlMinutes) 판정에 사용한다.
     */
    fun findExpirableBookingCandidates(ttlMinutes: Long, afterId: Long, limit: Int): List<BookingExpiryCandidate> {
        val threshold = ZonedDateTime.now().minusMinutes(ttlMinutes)
        return bookingRepository.findPendingCreatedBefore(threshold, afterId, limit)
    }

    /**
     * W1-11c 5차 재설계 — 만료 후보 중 실제로 만료시킬 대상을 최종 판정한다. payment로부터
     * 받은 [liveSince](orderId → live payment 발급 시각 최댓값)·[settledOrderIds](COMPLETED
     * 주문 id 집합)만으로 판단하므로 도메인 교차가 아니다 — 크로스 컨텍스트 조합 자체(payment
     * 조회 → 값 변환)는 application(ExpirePendingBookingsUseCase)이 수행하고, 이 메서드는
     * booking 자신의 정책(두 TTL)만 적용한다.
     *
     * **앵커 정정(4차 → 5차)**: 4차는 느린 TTL을 `candidate.createdAt`(예약 생성 시각)에
     * 적용했으나, `POST /payments/prepare`가 기존 주문에 새 payment 행을 만드는 가동 중
     * 경로라 예약이 오래전에 생성됐어도 결제가 방금 살아날 수 있다 — 그 경우 예약 생성 시각
     * 기준으로는 다음 주기에 오만료됐다. 앵커를 **payment 발급 시각의 최댓값**(liveSince)으로
     * 옮겨 "결제가 언제 살아났는가"를 정확히 반영한다.
     *
     * - settled 포함: 돈을 받았다 — 항상 제외(절대 만료 금지).
     * - liveSince에 값이 있고 그 값이 느린 TTL(readyTtlMinutes)을 아직 지나지 않음: 결제창에
     *   머무는 사용자로 보아 제외.
     * - 그 외(liveSince에 없음 — 빠른 TTL 후보 조회 자체를 통과한 결제 미시작/실패/취소/환불,
     *   또는 liveSince가 readyTtl까지 지난 live): 만료 대상에 포함.
     */
    fun filterExpirable(
        candidates: List<BookingExpiryCandidate>,
        liveSince: Map<Long, ZonedDateTime>,
        settledOrderIds: Set<Long>,
        readyTtlMinutes: Long,
    ): BookingExpiryFilterResult {
        val readyThreshold = ZonedDateTime.now().minusMinutes(readyTtlMinutes)
        val settled = candidates.filter { settledOrderIds.contains(it.bookingId) }
        val expirableIds = candidates.filterNot { settledOrderIds.contains(it.bookingId) }
            .filter { candidate -> isExpirableByLiveness(candidate.bookingId, liveSince, readyThreshold) }
            .map { it.bookingId }
        return BookingExpiryFilterResult(expirableIds = expirableIds, skippedSettledCount = settled.size)
    }

    private fun isExpirableByLiveness(
        bookingId: Long,
        liveSince: Map<Long, ZonedDateTime>,
        readyThreshold: ZonedDateTime,
    ): Boolean {
        val liveAnchor = liveSince[bookingId] ?: return true
        return liveAnchor.isBefore(readyThreshold)
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
