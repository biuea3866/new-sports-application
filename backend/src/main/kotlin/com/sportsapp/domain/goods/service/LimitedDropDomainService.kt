package com.sportsapp.domain.goods.service

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.exceptions.RedisLockException
import com.sportsapp.domain.goods.dto.LimitedDropStats
import com.sportsapp.domain.goods.dto.PurchaseLimitedDropCommand
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.exception.LimitedDropNotFoundException
import com.sportsapp.domain.goods.exception.LimitedDropPerUserLimitExceededException
import com.sportsapp.domain.goods.exception.LimitedDropSoldOutException
import com.sportsapp.domain.goods.exception.LimitedDropThrottledException
import com.sportsapp.domain.goods.exception.LimitedDropTooEarlyException
import com.sportsapp.domain.goods.gateway.DropReservationStore
import com.sportsapp.domain.goods.gateway.RejectKind
import com.sportsapp.domain.goods.gateway.ReservationResult
import com.sportsapp.domain.goods.repository.LimitedDropRepository
import com.sportsapp.domain.goods.vo.OrderItemInput
import java.time.Duration
import java.time.ZonedDateTime
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
 */
@Service
class LimitedDropDomainService(
    private val limitedDropRepository: LimitedDropRepository,
    private val dropReservationStore: DropReservationStore,
    private val goodsDomainService: GoodsDomainService,
    private val domainEventPublisher: DomainEventPublisher,
) {
    fun purchase(command: PurchaseLimitedDropCommand): Pair<LimitedDrop, GoodsOrder> {
        val drop = findById(command.dropId)
        validatePurchasable(drop)
        return drop to admit(drop, command)
    }

    fun openDrop(dropId: Long): LimitedDrop {
        val drop = findById(dropId)
        drop.open()
        val saved = limitedDropRepository.save(drop)
        val ttl = Duration.between(ZonedDateTime.now(), saved.closeAt).plusHours(1)
        dropReservationStore.seedIfAbsent(saved.id, saved.limitedQuantity, ttl)
        return saved
    }

    /** 회차 상세 조회(FR-9 인접 조회). Redis remaining이 시드되지 않았으면 null을 그대로 넘긴다. */
    fun getView(dropId: Long): Pair<LimitedDrop, Int?> {
        val drop = findById(dropId)
        return drop to dropReservationStore.remaining(dropId)
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
     */
    fun reconcileAllActive() {
        limitedDropRepository.findAllActive().forEach { reconcileDrift(it) }
    }

    /** 단일 회차 대사. 테스트·수동 트리거 대상 진입점. */
    fun reconcile(dropId: Long) {
        reconcileDrift(findById(dropId))
    }

    /**
     * Redis remaining ↔ DB stock 드리프트를 판정해 오버셀 감지 시 이벤트를 적재·발행한다.
     * 오버셀 판정: 계산된 판매량(limitedQuantity - remaining)이 limitedQuantity를 초과하거나,
     * 상품 재고가 음수로 정합이 깨진 경우.
     */
    private fun reconcileDrift(drop: LimitedDrop) {
        val remaining = dropReservationStore.remaining(drop.id) ?: drop.limitedQuantity
        val stockQuantity = goodsDomainService.getProductWithStock(drop.productId).stockQuantity
        val computedSold = drop.limitedQuantity - remaining
        val isOversold = computedSold > drop.limitedQuantity || stockQuantity < 0
        if (!isOversold) return
        val detectedQuantity = maxOf(computedSold, drop.limitedQuantity - stockQuantity)
        drop.recordOversold(detectedQuantity)
        domainEventPublisher.publishAll(drop.pullDomainEvents())
    }

    private fun findById(dropId: Long): LimitedDrop =
        limitedDropRepository.findById(dropId) ?: throw LimitedDropNotFoundException(dropId)

    /**
     * 판매자 회차 개설(BE-08). 대상 상품의 현재 재고를 조회해 요청 수량이 재고를 넘지 않는지
     * 검증한 뒤 [LimitedDrop.create]로 저장하고, 종료 시각 기준 TTL로 Redis 카운터를 시드한다.
     */
    fun createDrop(
        productId: Long,
        openAt: ZonedDateTime,
        closeAt: ZonedDateTime,
        limitedQuantity: Int,
        perUserLimit: Int,
        ownerUserId: Long,
    ): LimitedDrop {
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
        return saved
    }

    private fun seedReservationStore(drop: LimitedDrop) {
        val ttl = Duration.between(ZonedDateTime.now(), drop.closeAt).plusHours(1)
        dropReservationStore.seedIfAbsent(drop.id, drop.limitedQuantity, ttl)
    }

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
     * [ReservationResult.Admitted]만 완충 permit을 보유한다 — 성공/실패에 따라 [persistOrRelease]로
     * confirmSuccess/cancel을 짝지어 반납한다.
     *
     * [ReservationResult.AlreadyReserved]는 멱등 재시도로, `reserve.lua`가 permit 획득 이전(순수 Redis
     * 판정 단계)에 즉시 반환한 결과다 — permit을 보유하지 않으므로 fail-open과 동일하게 [persistOrder]만
     * 호출한다 (confirmSuccess·cancel 호출 시 permit 풀이 멱등 재시도마다 영구 팽창한다).
     */
    private fun admit(drop: LimitedDrop, command: PurchaseLimitedDropCommand): GoodsOrder {
        val result = tryReserve(drop, command) ?: return persistOrder(drop, command)
        return when (result) {
            is ReservationResult.Admitted -> persistOrRelease(drop, command)
            is ReservationResult.AlreadyReserved -> persistOrder(drop, command)
            is ReservationResult.SoldOut -> {
                recordRejectSafely(drop.id, RejectKind.SOLD_OUT)
                throw LimitedDropSoldOutException(drop.id)
            }
            is ReservationResult.Throttled -> throw LimitedDropThrottledException(drop.id)
            is ReservationResult.PerUserLimitExceeded ->
                throw LimitedDropPerUserLimitExceededException(drop.id, result.limit)
        }
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

    private fun persistOrRelease(drop: LimitedDrop, command: PurchaseLimitedDropCommand): GoodsOrder {
        val order = try {
            goodsDomainService.createPendingOrder(command.userId, itemsOf(drop, command), command.idempotencyKey)
        } catch (exception: Exception) {
            dropReservationStore.cancel(drop.id, command.userId, command.quantity, command.idempotencyKey)
            throw exception
        }
        dropReservationStore.confirmSuccess(drop.id, command.userId, command.idempotencyKey)
        return order
    }

    private fun itemsOf(drop: LimitedDrop, command: PurchaseLimitedDropCommand): List<OrderItemInput> =
        listOf(OrderItemInput(drop.productId, command.quantity))
}
