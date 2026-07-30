package com.sportsapp.infrastructure.goods.mysql

import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import org.springframework.data.jpa.repository.JpaRepository

interface LimitedDropJpaRepository : JpaRepository<LimitedDrop, Long> {

    /**
     * 대상 상품의 활성 회차(SCHEDULED|OPEN|SOLD_OUT, CLOSED 제외) 중 openAt 최신 1건을 조회한다.
     * 실제 판매 가능 여부(시작 게이트·품절)는 [LimitedDrop.validatePurchasable]가 판정한다.
     */
    fun findFirstByProductIdAndStatusInAndDeletedAtIsNullOrderByOpenAtDesc(
        productId: Long,
        statuses: List<LimitedDropStatus>,
    ): LimitedDrop?

    /** 여러 상품의 활성 회차(SCHEDULED|OPEN|SOLD_OUT, CLOSED 제외)를 한 번에 조회한다. */
    fun findAllByProductIdInAndStatusInAndDeletedAtIsNull(
        productIds: List<Long>,
        statuses: List<LimitedDropStatus>,
    ): List<LimitedDrop>

    /** 대사(reconciliation) 대상 활성 회차(SCHEDULED|OPEN, deleted 제외) 전체를 조회한다. */
    fun findAllByStatusInAndDeletedAtIsNull(statuses: List<LimitedDropStatus>): List<LimitedDrop>
}
