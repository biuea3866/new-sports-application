package com.sportsapp.domain.goods.repository

import com.sportsapp.domain.goods.entity.LimitedDrop

/**
 * LimitedDrop 영속화 계약 (domain interface). 구현체는 infrastructure의
 * LimitedDropRepositoryImpl(+ JPA)이 담당한다 (BE-05).
 */
interface LimitedDropRepository {
    fun save(limitedDrop: LimitedDrop): LimitedDrop
    fun findById(id: Long): LimitedDrop?

    /** 대상 상품의 활성(OPEN) 회차 1건을 조회한다. */
    fun findOpenByProductId(productId: Long): LimitedDrop?

    /** 여러 상품의 활성 회차를 한 번에 조회한다(N+1 방지, `/products` 목록 응답 결합용). */
    fun findOpenByProductIds(productIds: List<Long>): List<LimitedDrop>

    /** 대사(reconciliation) 대상 활성 회차(SCHEDULED|OPEN) 전체를 조회한다. */
    fun findAllActive(): List<LimitedDrop>
}
