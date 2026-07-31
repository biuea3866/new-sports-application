package com.sportsapp.infrastructure.message.gateway

import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.message.gateway.GoodsProductGateway
import org.springframework.stereotype.Component

/**
 * `GoodsProductGateway` 구현체 (BE-11, TDD FR-18 / PH0-03).
 *
 * goods 도메인의 `ProductRepository`(=`products` 테이블)를 직접 읽지 않고, 공급자
 * `GoodsDomainService.findOwnerIdBy`를 경유한다 — message 인프라가 goods 컨텍스트의
 * 저장소를 직접 아는 것을 막는다(PH0-03, 근거: 20260728-ph0-결합해소-거버넌스정합-tdd.md 항목 1).
 * `FacilityOwnershipGatewayImpl`(booking -> facility)과 동일한 크로스 도메인 내부 조회
 * 게이트웨이 패턴 — 외부 시스템 Client 가 아니다.
 */
@Component
class GoodsProductGatewayImpl(
    private val goodsDomainService: GoodsDomainService,
) : GoodsProductGateway {

    override fun findOwnerId(productId: Long): Long =
        goodsDomainService.findOwnerIdBy(productId)
}
