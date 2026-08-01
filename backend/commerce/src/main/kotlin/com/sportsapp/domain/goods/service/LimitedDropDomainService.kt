package com.sportsapp.domain.goods.service

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.exceptions.RedisLockException
import com.sportsapp.domain.goods.dto.LimitedDropStats
import com.sportsapp.domain.goods.dto.PurchaseLimitedDropCommand
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.exception.LimitedDropNotFoundException
import com.sportsapp.domain.goods.exception.LimitedDropPerUserLimitExceededException
import com.sportsapp.domain.goods.exception.LimitedDropSoldOutException
import com.sportsapp.domain.goods.exception.LimitedDropThrottledException
import com.sportsapp.domain.goods.exception.LimitedDropTooEarlyException
import com.sportsapp.domain.goods.gateway.DropReservationCompensator
import com.sportsapp.domain.goods.gateway.DropReservationStore
import com.sportsapp.domain.goods.gateway.PendingReservation
import com.sportsapp.domain.goods.gateway.RejectKind
import com.sportsapp.domain.goods.gateway.ReservationResult
import com.sportsapp.domain.goods.repository.LimitedDropRepository
import com.sportsapp.domain.goods.vo.OrderItemInput
import java.math.BigDecimal
import java.time.Duration
import java.time.ZonedDateTime
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service

/**
 * 한정판 구매 오케스트레이션 (ADR-001 심층 방어, ADR-003 goods 제공 인터페이스 무변경 재사용).
 *
 * [goodsDomainService.createPendingOrder]는 이 도메인의 기존 UseCase 경계(ADR-003)로,
 * 이 서비스는 그 앞단에 Redis 입장 게이트를 심층 방어로 추가할 뿐 무변경 재사용한다(FR-3).
 *
 * [domainEventPublisher]는 구매 흐름에서는 사용하지 않고, [reconcile]/[reconcileAllActive]의
 * 오버셀 감지([LimitedDrop.recordOversold] 적재분) 발행에만 사용한다 (BE-11, Observability).
 *
 * [W1-DEBT-01] TooManyFunctions 억제 근거: 이 서비스는 "한정판 구매 오케스트레이션"이라는 단일
 * 책임 아래 구매(purchase)·개설(createDrop/openDrop)·조회(getView/getStats)·대사(reconcile*)를
 * 담당한다. 각 흐름은 ADR-001/ADR-003/FIX-02~04 문서화된 fail-open·롤백 보상·언더셀 복원
 * 계약으로 서로 얽혀 있어(예: [admit]이 [persistWithThrottle]·[registerCancelOnRollback]과
 * 공유하는 상태), 클래스를 쪼개면 이 계약이 여러 클래스에 흩어져 동작 변경 위험 없이는
 * 분리할 수 없다. 이번 정리는 정적 분석 부채 정리이지 리팩토링 범위가 아니므로 억제한다.
 */
@Suppress("TooManyFunctions")
@Service
class LimitedDropDomainService(
    private val limitedDropRepository: LimitedDropRepository,
    private val dropReservationStore: DropReservationStore,
    private val goodsDomainService: GoodsDomainService,
    private val domainEventPublisher: DomainEventPublisher,
    private val dropReservationCompensator: DropReservationCompensator,
    /**
     * FIX-04 롤백 스위치 — OFF면 언더셀 복원을 건너뛰고 기존 오버셀 감지만 수행한다.
     * `application.yml`에 키를 추가하지 않는다 — env(`APP_LIMITED_DROP_RECONCILIATION_UNDER_SELL_ENABLED`)로만
     * 재정의한다(같은 wave의 yml 소유권 충돌 방지, FIX-02/FIX-03 선례).
     */
    @Value("\${app.limited-drop.reconciliation.under-sell-enabled:true}")
    private val underSellReconciliationEnabled: Boolean = true,
    /**
     * 예약 생성 후 이 시간(초)이 지나야 "고립 예약" 후보로 본다. 진행 중인 요청을 오판해 취소하면
     * 오버셀이 되므로, 백엔드 최대 요청 수명(풀 대기 5s + 재시도 예산 약 2s, FIX-03 기준 약 7s)보다
     * 충분히 커야 한다. 기본 60초.
     */
    @Value("\${app.limited-drop.reconciliation.under-sell-grace-seconds:60}")
    private val underSellGraceSeconds: Long = 60,
) {
    private val log = LoggerFactory.getLogger(LimitedDropDomainService::class.java)

    fun purchase(command: PurchaseLimitedDropCommand): Pair<LimitedDrop, GoodsOrder> {
        val drop = findById(command.dropId)
        validatePurchasable(drop)
        return drop to admit(drop, command)
    }

    /**
     * 판매자 콘솔/스케줄러 연동 전 진입점(현재 호출부 없음, 죽은 코드 아님 — [LimitedDrop.open] 상태
     * 전이·재시드 계약을 보존한다). 실제 판매 게이트 판정은 항상 [LimitedDrop.effectiveStatus]가
     * now·remaining 기준으로 실시간 파생하므로, 이 메서드가 아직 호출되지 않아도 구매 흐름은 정확하다.
     */
    fun openDrop(dropId: Long): LimitedDrop {
        val drop = findById(dropId)
        drop.open()
        val saved = limitedDropRepository.save(drop)
        dropReservationStore.seedIfAbsent(saved.id, saved.limitedQuantity, ttlUntilClose(saved.closeAt))
        return saved
    }

    /**
     * 회차 상세 조회(FR-9 인접 조회). Redis remaining이 시드되지 않았으면 null을 그대로 넘긴다.
     * 연결된 상품(이름·이미지·가격)을 함께 반환한다 — 상세 화면이 productId 대신 사람이 읽는
     * 이름을 노출하고 결제 amount 를 전달하기 위해 필요하다.
     */
    fun getView(dropId: Long): Triple<LimitedDrop, Int?, ProductWithStock> {
        val drop = findById(dropId)
        val remaining = dropReservationStore.remaining(dropId)
        return Triple(drop, remaining, goodsDomainService.getProductWithStock(drop.productId))
    }

    /**
     * FR-9 집계. successCount는 전용 테이블 없이 `limitedQuantity - remaining`으로 파생하고(design-db
     * "FR-9 집계" 참조), 거부 건수는 Redis 카운터([DropReservationStore.rejectCounts])를 그대로 결합한다.
     */
    fun getStats(dropId: Long): LimitedDropStats {
        val drop = findById(dropId)
        val remaining = dropReservationStore.remaining(dropId) ?: drop.limitedQuantity
        val rejectCounts = dropReservationStore.rejectCounts(dropId)
        return LimitedDropStats(
            successCount = (drop.limitedQuantity - remaining).toLong(),
            soldOutRejectCount = rejectCounts.soldOutCount,
            tooEarlyRejectCount = rejectCounts.tooEarlyCount,
        )
    }

    /**
     * 활성 회차(SCHEDULED|OPEN) 전체에 대해 대사(reconciliation)를 수행한다 (Observability, ADR-001 실패 경로).
     * [DropReconciliationWorker]가 스케줄 주기마다 호출한다.
     *
     * 활성 회차가 없으면 Redis·상품 조회 없이 즉시 반환한다(대부분의 통합 테스트가 해당). 회차별 대사는
     * [reconcileDriftSafely]로 격리해 한 회차의 Redis 장애가 나머지 회차 대사를 막지 않게 한다.
     */
    fun reconcileAllActive() {
        val activeDrops = limitedDropRepository.findAllActive()
        if (activeDrops.isEmpty()) return
        activeDrops.forEach { reconcileDriftSafely(it) }
    }

    /** 단일 회차 대사. 테스트·수동 트리거 대상 진입점. */
    fun reconcile(dropId: Long) {
        reconcileDrift(findById(dropId))
    }

    /** [reconcileDrift]를 Redis 장애로부터 격리한다 — fail-open, 로그만 남기고 다음 회차로 진행한다. */
    private fun reconcileDriftSafely(drop: LimitedDrop) {
        try {
            reconcileDrift(drop)
        } catch (exception: DataAccessException) {
            log.warn("LimitedDropDomainService: dropId={} 대사 중 인프라 접근 실패로 건너뜁니다", drop.id, exception)
        } catch (exception: RedisLockException) {
            log.warn("LimitedDropDomainService: dropId={} 대사 중 Redis 락 장애로 건너뜁니다", drop.id, exception)
        }
    }

    /**
     * Redis remaining ↔ DB stock/주문 드리프트를 판정한다 — 오버셀(FIX-04 이전부터, 초과 판매)과
     * 언더셀(FIX-04, 예약-주문 불일치로 고립된 예약) 두 방향을 모두 감지한다.
     */
    private fun reconcileDrift(drop: LimitedDrop) {
        reconcileOversell(drop)
        reconcileUnderSell(drop)
    }

    /**
     * 오버셀 판정: 계산된 판매량(limitedQuantity - remaining)이 limitedQuantity를 초과하거나,
     * 상품 재고가 음수로 정합이 깨진 경우. 감지 시 이벤트를 적재·발행한다.
     */
    private fun reconcileOversell(drop: LimitedDrop) {
        val remaining = dropReservationStore.remaining(drop.id) ?: drop.limitedQuantity
        val stockQuantity = goodsDomainService.getProductWithStock(drop.productId).stockQuantity
        val computedSold = drop.limitedQuantity - remaining
        val isOversold = computedSold > drop.limitedQuantity || stockQuantity < 0
        if (!isOversold) return
        val detectedQuantity = maxOf(computedSold, drop.limitedQuantity - stockQuantity)
        drop.recordOversold(detectedQuantity)
        domainEventPublisher.publishAll(drop.pullDomainEvents())
    }

    /**
     * 언더셀(예약-주문 불일치) 판정 (FIX-04). 예약 마커는 있으나 대응 `goods_orders` 행이 없고
     * 유예 시간([underSellGraceSeconds])이 지난 "고립 예약"만 [DropReservationStore.restoreOrphanedReservation]로
     * 복원한다 — 진행 중인 요청(유예 이내)은 [DropReservationStore.scanStaleReservations]가 이미 제외한다.
     *
     * 복원 대상이 없으면 Redis 쓰기 명령을 발행하지 않는다(early return).
     */
    // guard clause 다수 사용의 부산물(private-be-code-convention 권장) — 플래그 OFF·스캔
    // 결과 없음·복원 대상 없음 각각의 조기 반환이 병합보다 읽기 쉽다.
    @Suppress("ReturnCount")
    private fun reconcileUnderSell(drop: LimitedDrop) {
        if (!underSellReconciliationEnabled) return
        val staleReservations = dropReservationStore.scanStaleReservations(drop.id, underSellGraceSeconds)
        if (staleReservations.isEmpty()) return
        val orphaned = staleReservations.filterNot { goodsDomainService.hasOrderFor(it.idempotencyKey) }
        if (orphaned.isEmpty()) return
        val restoredCount = orphaned.count { restoreOrphan(drop.id, it) }
        drop.recordUnderRestored(restoredCount, orphaned.size)
        domainEventPublisher.publishAll(drop.pullDomainEvents())
    }

    private fun restoreOrphan(dropId: Long, reservation: PendingReservation): Boolean =
        dropReservationStore.restoreOrphanedReservation(
            dropId = dropId,
            userId = reservation.userId,
            quantity = reservation.quantity,
            idempotencyKey = reservation.idempotencyKey,
        )

    private fun findById(dropId: Long): LimitedDrop =
        limitedDropRepository.findById(dropId) ?: throw LimitedDropNotFoundException(dropId)

    /**
     * 판매자 회차 개설(BE-08). 대상 상품의 현재 재고를 조회해 요청 수량이 재고를 넘지 않는지
     * 검증한 뒤 [LimitedDrop.create]로 저장하고, 종료 시각 기준 TTL로 Redis 카운터를 시드한다.
     * 상품 가격도 함께 반환한다(POST/GET 공용 응답 [LimitedDropView]가 필요로 한다).
     */
    fun createDrop(
        productId: Long,
        openAt: ZonedDateTime,
        closeAt: ZonedDateTime,
        limitedQuantity: Int,
        perUserLimit: Int,
        ownerUserId: Long,
    ): Pair<LimitedDrop, ProductWithStock> {
        val productWithStock = goodsDomainService.getProductWithStock(productId)
        productWithStock.requireOwnedBy(ownerUserId)
        productWithStock.validateQuantityWithin(limitedQuantity)
        val saved = limitedDropRepository.save(
            LimitedDrop.create(
                productId = productId,
                openAt = openAt,
                closeAt = closeAt,
                limitedQuantity = limitedQuantity,
                perUserLimit = perUserLimit,
            )
        )
        seedReservationStore(saved)
        return saved to productWithStock
    }

    private fun seedReservationStore(drop: LimitedDrop) {
        dropReservationStore.seedIfAbsent(drop.id, drop.limitedQuantity, ttlUntilClose(drop.closeAt))
    }

    /**
     * closeAt 기준 TTL(+1h 여유)을 계산한다. closeAt이 이미 과거면(운영 오조작 등) 음수 TTL이 되어
     * `SET NX PX`가 즉시 만료하는 키를 시드하는 사고를 막기 위해 최소 1시간으로 보정한다(코드 리뷰 p3).
     */
    private fun ttlUntilClose(closeAt: ZonedDateTime): Duration =
        Duration.between(ZonedDateTime.now(), closeAt).plusHours(1).coerceAtLeast(Duration.ofHours(1))

    /**
     * 판매 시작 게이트(FR-2) 판정. [LimitedDrop.validatePurchasable]이 던지는 SoldOut·TooEarly는
     * FR-9 거부 집계 대상이라 [recordRejectSafely]로 카운터를 남긴 뒤 원본 예외를 그대로 재전파한다.
     */
    private fun validatePurchasable(drop: LimitedDrop) {
        try {
            drop.validatePurchasable()
        } catch (exception: LimitedDropTooEarlyException) {
            recordRejectSafely(drop.id, RejectKind.TOO_EARLY)
            throw exception
        } catch (exception: LimitedDropSoldOutException) {
            recordRejectSafely(drop.id, RejectKind.SOLD_OUT)
            throw exception
        }
    }

    /**
     * SoldOut·PerUserLimitExceeded는 Redis 판정만으로 즉시 거부한다(완충 불필요, 값싼 컷오프, ADR-003
     * 순서: FR-8/FR-6가 FR-7보다 먼저). 그 외 DB 쓰기에 도달하는 모든 경로 — Admitted·AlreadyReserved(멱등
     * 재시도)·fail-open(Redis 장애로 [tryReserve]가 null 반환) — 는 [persistWithThrottle]로 완충(FR-7)
     * 게이트를 거친다. Redis 장애 fail-open이 완충을 우회하던 것이 코드 리뷰 p1 지적 사항이었다 —
     * DB는 Redis 가용성과 무관하게 항상 완충 permit 뒤에서만 쓰기를 받는다.
     */
    private fun admit(drop: LimitedDrop, command: PurchaseLimitedDropCommand): GoodsOrder =
        when (val result = tryReserve(drop, command)) {
            // fail-open(Redis 장애)은 이번 시도가 슬롯을 새로 차감하지 않았으므로 registerCancelOnRollback을
            // 호출하지 않는다 — 등록하지 않아도 보상 공백이 생기지 않는다: 이 시도가 이전에 이미 Admitted였던
            // 같은 재시도 시퀀스의 마지막 시도라면, DropReservationCompensatorImpl이 이전 Admitted 시도의
            // 취소 후보를 RetryContext에 이미 남겨 두었고, 그 후보는 이 시도가 실패해도 그대로 유지된다
            // (재시도 시퀀스 종료 시 DropReservationCompensationRetryListener가 읽어 취소한다, code-review p3).
            null -> persistWithThrottle(drop, command, restoreOnFailure = false)
            is ReservationResult.Admitted -> {
                registerCancelOnRollback(drop.id, command, admittedThisAttempt = true)
                persistWithThrottle(drop, command, restoreOnFailure = true)
            }
            is ReservationResult.AlreadyReserved -> {
                registerCancelOnRollback(drop.id, command, admittedThisAttempt = false)
                persistWithThrottle(drop, command, restoreOnFailure = false)
            }
            is ReservationResult.SoldOut -> {
                recordRejectSafely(drop.id, RejectKind.SOLD_OUT)
                throw LimitedDropSoldOutException(drop.id)
            }
            is ReservationResult.PerUserLimitExceeded ->
                throw LimitedDropPerUserLimitExceededException(drop.id, result.limit)
        }

    /**
     * 커밋 단계 실패까지 포괄하는 롤백 보상을 등록한다(FIX-02) — Redis 예약이 실제로 존재하는
     * 두 경로(Admitted로 방금 차감했거나, 같은 재시도 시퀀스 안에서 AlreadyReserved로 이어지는
     * 경우)에서만 호출한다. 재시도 예산이 소진되는 마지막 시도의 롤백에서만 실제 취소가 실행된다
     * ([DropReservationCompensatorImpl] 참조).
     */
    private fun registerCancelOnRollback(dropId: Long, command: PurchaseLimitedDropCommand, admittedThisAttempt: Boolean) {
        dropReservationCompensator.registerCancelOnRollback(
            dropId = dropId,
            userId = command.userId,
            quantity = command.quantity,
            idempotencyKey = command.idempotencyKey,
            admittedThisAttempt = admittedThisAttempt,
        )
    }

    /** FR-9 거부 카운터 증가. Redis 장애는 fail-open — 구매 흐름(예외 전파)에 영향을 주지 않는다. */
    private fun recordRejectSafely(dropId: Long, kind: RejectKind) {
        try {
            dropReservationStore.recordReject(dropId, kind)
        } catch (exception: DataAccessException) {
            // fail-open: 거부 카운터는 휘발성 운영 지표(P2)일 뿐, 구매 결과에 영향을 주지 않는다.
        } catch (exception: RedisLockException) {
            // fail-open
        }
    }

    private fun tryReserve(drop: LimitedDrop, command: PurchaseLimitedDropCommand): ReservationResult? =
        try {
            dropReservationStore.reserve(
                dropId = drop.id,
                userId = command.userId,
                quantity = command.quantity,
                perUserLimit = drop.perUserLimit,
                idempotencyKey = command.idempotencyKey,
            )
        } catch (exception: DataAccessException) {
            null
        } catch (exception: RedisLockException) {
            null
        }

    private fun persistOrder(drop: LimitedDrop, command: PurchaseLimitedDropCommand): GoodsOrder =
        goodsDomainService.createPendingOrder(command.userId, itemsOf(drop, command), command.idempotencyKey)

    /**
     * DB 쓰기 직전 완충(FR-7) permit을 획득한다 — Redis 판정 성공 여부(Admitted/AlreadyReserved/fail-open)와
     * 무관하게 항상 거친다. permit 획득 실패 시 [restoreOnFailure]가 true(=Admitted로 Redis 슬롯을 실제
     * 차감한 경우)면 [DropReservationStore.cancel]로 슬롯을 복원한 뒤 429로 거부한다.
     *
     * AlreadyReserved·fail-open은 이번 호출에서 Redis 슬롯을 새로 차감하지 않았으므로(AlreadyReserved는
     * 과거에 이미 차감됐고, fail-open은 Redis 자체에 도달하지 못했다) [restoreOnFailure]가 false다 —
     * cancel을 호출하면 AlreadyReserved의 멱등 마커까지 삭제되어 재구매가 가능해지는 오동작이 생긴다.
     */
    private fun persistWithThrottle(
        drop: LimitedDrop,
        command: PurchaseLimitedDropCommand,
        restoreOnFailure: Boolean,
    ): GoodsOrder {
        if (!dropReservationStore.tryAcquireThrottle()) {
            if (restoreOnFailure) dropReservationStore.cancel(drop.id, command.userId, command.quantity, command.idempotencyKey)
            throw LimitedDropThrottledException(drop.id)
        }
        return try {
            val order = persistOrder(drop, command)
            if (restoreOnFailure) dropReservationStore.confirmSuccess(drop.id, command.userId, command.idempotencyKey)
            order
        } catch (exception: Exception) {
            if (restoreOnFailure) dropReservationStore.cancel(drop.id, command.userId, command.quantity, command.idempotencyKey)
            throw exception
        } finally {
            dropReservationStore.releaseThrottle()
        }
    }

    private fun itemsOf(drop: LimitedDrop, command: PurchaseLimitedDropCommand): List<OrderItemInput> =
        listOf(OrderItemInput(drop.productId, command.quantity))
}
