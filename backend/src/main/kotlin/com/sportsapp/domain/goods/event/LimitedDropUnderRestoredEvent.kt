package com.sportsapp.domain.goods.event

import com.sportsapp.domain.common.AbstractDomainEvent

/**
 * 리컨실리에이션 언더셀(예약-주문 불일치) 복원 이벤트 (FIX-04).
 *
 * 예약 마커는 있으나 대응 `goods_orders` 행이 없고 유예 시간이 지난 "고립 예약"을
 * [com.sportsapp.domain.goods.entity.LimitedDrop.recordUnderRestored]를 통해 적재한다.
 *
 * [restoredCount]는 이번 주기에 실제로 복원(cancel.lua Restored)된 건수, [staleReservationCount]는
 * 복원 대상으로 판정된 전체 후보 수 — 두 값이 반복적으로 벌어지면 복원 경로 자체의 누수 신호로 쓴다.
 */
class LimitedDropUnderRestoredEvent(
    val dropId: Long,
    val productId: Long,
    val restoredCount: Int,
    val staleReservationCount: Int,
    val source: String = "under-sell-reconciliation",
    val severity: String = "warning",
) : AbstractDomainEvent(aggregateId = dropId)
