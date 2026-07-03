package com.sportsapp.domain.goods.service

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.exceptions.RedisLockException
import com.sportsapp.domain.goods.dto.PurchaseLimitedDropCommand
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.exception.LimitedDropNotFoundException
import com.sportsapp.domain.goods.exception.LimitedDropPerUserLimitExceededException
import com.sportsapp.domain.goods.exception.LimitedDropSoldOutException
import com.sportsapp.domain.goods.exception.LimitedDropThrottledException
import com.sportsapp.domain.goods.gateway.DropReservationStore
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
 * [domainEventPublisher]는 이 티켓 범위의 구매 흐름에서는 사용하지 않는다 — 리컨실리에이션
 * 입력([LimitedDrop.recordOversold] 적재분 발행)은 후속 티켓 범위이며, 이 서비스에는
 * 클래스 의존 계약([DomainEventPublisher] 주입)만 선반영한다.
 */
@Service
class LimitedDropDomainService(
    private val limitedDropRepository: LimitedDropRepository,
    private val dropReservationStore: DropReservationStore,
    private val goodsDomainService: GoodsDomainService,
    private val domainEventPublisher: DomainEventPublisher,
) {
    fun purchase(command: PurchaseLimitedDropCommand): GoodsOrder {
        val drop = findOpenDrop(command.productId)
        drop.validatePurchasable()
        return admit(drop, command)
    }

    fun openDrop(dropId: Long): LimitedDrop {
        val drop = limitedDropRepository.findById(dropId)
            ?: throw LimitedDropNotFoundException(dropId)
        drop.open()
        val saved = limitedDropRepository.save(drop)
        val ttl = Duration.between(ZonedDateTime.now(), saved.closeAt).plusHours(1)
        dropReservationStore.seedIfAbsent(saved.id, saved.limitedQuantity, ttl)
        return saved
    }

    private fun findOpenDrop(productId: Long): LimitedDrop =
        limitedDropRepository.findOpenByProductId(productId)
            ?: throw LimitedDropNotFoundException(productId)

    /**
     * [ReservationResult.Admitted]만 완충 permit을 보유한다 — 성공/실패에 따라 [persistOrRelease]로
     * confirmSuccess/cancel을 짝지어 반납한다.
     *
     * [ReservationResult.AlreadyReserved]는 멱등 재시도로, `reserve.lua`가 permit 획득 이전(순수 Redis
     * 판정 단계)에 즉시 반환한 결과다 — permit을 보유하지 않으므로 fail-open과 동일하게 [persistOrder]만
     * 호출한다 (confirmSuccess·cancel 호출 시 permit 풀이 멱등 재시도마다 영구 팽창한다).
     */
    private fun admit(drop: LimitedDrop, command: PurchaseLimitedDropCommand): GoodsOrder {
        val result = tryReserve(drop, command) ?: return persistOrder(command)
        return when (result) {
            is ReservationResult.Admitted -> persistOrRelease(drop, command)
            is ReservationResult.AlreadyReserved -> persistOrder(command)
            is ReservationResult.SoldOut -> throw LimitedDropSoldOutException(drop.id)
            is ReservationResult.Throttled -> throw LimitedDropThrottledException(drop.id)
            is ReservationResult.PerUserLimitExceeded ->
                throw LimitedDropPerUserLimitExceededException(drop.id, result.limit)
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

    private fun persistOrder(command: PurchaseLimitedDropCommand): GoodsOrder =
        goodsDomainService.createPendingOrder(command.userId, itemsOf(command), command.idempotencyKey)

    private fun persistOrRelease(drop: LimitedDrop, command: PurchaseLimitedDropCommand): GoodsOrder {
        val order = try {
            goodsDomainService.createPendingOrder(command.userId, itemsOf(command), command.idempotencyKey)
        } catch (exception: Exception) {
            dropReservationStore.cancel(drop.id, command.userId, command.quantity, command.idempotencyKey)
            throw exception
        }
        dropReservationStore.confirmSuccess(drop.id, command.userId, command.idempotencyKey)
        return order
    }

    private fun itemsOf(command: PurchaseLimitedDropCommand): List<OrderItemInput> =
        listOf(OrderItemInput(command.productId, command.quantity))
}
