package com.sportsapp.domain.goods.entity

import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.goods.event.LimitedDropOversoldEvent
import com.sportsapp.domain.goods.exception.InvalidLimitedDropStateException
import com.sportsapp.domain.goods.exception.LimitedDropClosedException
import com.sportsapp.domain.goods.exception.LimitedDropSoldOutException
import com.sportsapp.domain.goods.exception.LimitedDropTooEarlyException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
import jakarta.persistence.Version
import java.time.ZonedDateTime

/**
 * 한정판 판매 회차 Aggregate Root (goods 도메인 신규 Aggregate — ADR-002).
 *
 * 판매 시작 게이트(FR-2)·상태 전이·오버셀 감지 이벤트 적재를 캡슐화한다.
 * 도메인 이벤트는 [AggregateRoot][com.sportsapp.domain.common.AggregateRoot]를
 * 상속하지 않고 [Booking][com.sportsapp.domain.booking.entity.Booking]과 동일하게
 * 로컬로 구현한다 — JpaAuditingBase(감사 컬럼)와 AggregateRoot(추상 클래스)를
 * Kotlin이 동시에 상속할 수 없기 때문이다 (단일 상속 제약, 이 코드베이스의 기존 관행).
 */
@Entity
@Table(name = "limited_drops")
class LimitedDrop private constructor(
    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "open_at", nullable = false)
    val openAt: ZonedDateTime,

    @Column(name = "close_at", nullable = false)
    val closeAt: ZonedDateTime,

    @Column(name = "limited_quantity", nullable = false)
    val limitedQuantity: Int,

    @Column(name = "per_user_limit", nullable = false)
    val perUserLimit: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private var status: LimitedDropStatus,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    @Version
    @Column(name = "version", nullable = false)
    val version: Long = 0L

    @Transient
    private var _domainEvents: MutableList<DomainEvent>? = null

    private val domainEvents: MutableList<DomainEvent>
        get() = _domainEvents ?: mutableListOf<DomainEvent>().also { _domainEvents = it }

    val currentStatus: LimitedDropStatus get() = status

    /**
     * 판매 시작 게이트(FR-2) + SOLD_OUT/CLOSED 즉시 거부를 판정한다.
     * 시각은 인자로 받지 않고 메서드 내부에서 [ZonedDateTime.now]로 해결한다.
     */
    fun validatePurchasable() {
        val now = ZonedDateTime.now()
        if (now.isBefore(openAt)) throw LimitedDropTooEarlyException(id, openAt)
        if (!now.isBefore(closeAt)) throw LimitedDropClosedException(id)
        if (status == LimitedDropStatus.SOLD_OUT) throw LimitedDropSoldOutException(id)
    }

    /**
     * 판매자 콘솔/스케줄러 연동 전 상태 전이 진입점(현재 호출부: [com.sportsapp.domain.goods.service
     * .LimitedDropDomainService.openDrop]만). 조회·집계는 이 메서드가 아니라 [effectiveStatus]가
     * now·remaining 기준으로 실시간 파생하므로, 이 메서드가 호출되지 않아도 구매·조회 정확성에는
     * 영향이 없다 — canTransitTo 상태 머신(상태전이표) 계약을 보존하기 위해 유지한다.
     */
    fun open() = transitTo(LimitedDropStatus.OPEN)

    /** [open] 과 동일한 이유로 유지 — 실제 SOLD_OUT 판정은 [effectiveStatus]가 remaining으로 파생한다. */
    fun markSoldOut() = transitTo(LimitedDropStatus.SOLD_OUT)

    /** [open] 과 동일한 이유로 유지 — 실제 CLOSED 판정은 [effectiveStatus]가 now·closeAt으로 파생한다. */
    fun close() = transitTo(LimitedDropStatus.CLOSED)

    /**
     * 조회·집계용 실시간 파생 상태. 영속 [status]는 [open]/[markSoldOut]/[close] 호출 여부에 의존해
     * 신뢰할 수 없으므로(상태 전이 표 정합, 코드 리뷰 p2), GET 응답·통계는 이 메서드로 일원화한다.
     *
     * 판정 순서: now < openAt → SCHEDULED, now ≥ closeAt → CLOSED, remaining ≤ 0 → SOLD_OUT, else OPEN.
     * [remaining] 이 null(Redis 미시드)이면 아직 소진분이 없는 것으로 간주해 SOLD_OUT으로 판정하지 않는다.
     */
    fun effectiveStatus(remaining: Int?): LimitedDropStatus {
        val now = ZonedDateTime.now()
        return when {
            now.isBefore(openAt) -> LimitedDropStatus.SCHEDULED
            !now.isBefore(closeAt) -> LimitedDropStatus.CLOSED
            remaining != null && remaining <= 0 -> LimitedDropStatus.SOLD_OUT
            else -> LimitedDropStatus.OPEN
        }
    }

    /**
     * 리컨실리에이션 드리프트 감지 시 오버셀 이벤트를 적재한다 — 판정은
     * [LimitedDropDomainService.reconcileDrift][com.sportsapp.domain.goods.service.LimitedDropDomainService]가
     * 계산된 판매량(`limitedQuantity - remaining`)이 `limitedQuantity`를 초과하거나 상품 재고가 음수로
     * 정합이 깨진 경우에 호출한다. DomainService가 이후 [pullDomainEvents]로 꺼내 발행한다.
     */
    fun recordOversold(detectedQuantity: Int) {
        domainEvents.add(
            LimitedDropOversoldEvent(
                dropId = id,
                productId = productId,
                detectedQuantity = detectedQuantity,
            )
        )
    }

    fun pullDomainEvents(): List<DomainEvent> {
        val events = domainEvents.toList()
        domainEvents.clear()
        return events
    }

    private fun transitTo(next: LimitedDropStatus) {
        if (!status.canTransitTo(next)) throw InvalidLimitedDropStateException(status, next)
        status = next
    }

    companion object {
        /** 신규 회차 개설 — 비즈니스 규칙 검증 포함. */
        fun create(
            productId: Long,
            openAt: ZonedDateTime,
            closeAt: ZonedDateTime,
            limitedQuantity: Int,
            perUserLimit: Int,
        ): LimitedDrop {
            require(openAt.isBefore(closeAt)) {
                "openAt must be before closeAt: openAt=$openAt, closeAt=$closeAt"
            }
            require(limitedQuantity > 0) { "limitedQuantity must be positive, got: $limitedQuantity" }
            require(perUserLimit > 0) { "perUserLimit must be positive, got: $perUserLimit" }
            return LimitedDrop(
                productId = productId,
                openAt = openAt,
                closeAt = closeAt,
                limitedQuantity = limitedQuantity,
                perUserLimit = perUserLimit,
                status = LimitedDropStatus.SCHEDULED,
            )
        }

        /** 영속화 계층 복원 — 검증 없이 필드를 그대로 복구한다. */
        fun reconstitute(
            productId: Long,
            openAt: ZonedDateTime,
            closeAt: ZonedDateTime,
            limitedQuantity: Int,
            perUserLimit: Int,
            status: LimitedDropStatus,
        ): LimitedDrop = LimitedDrop(
            productId = productId,
            openAt = openAt,
            closeAt = closeAt,
            limitedQuantity = limitedQuantity,
            perUserLimit = perUserLimit,
            status = status,
        )
    }
}
