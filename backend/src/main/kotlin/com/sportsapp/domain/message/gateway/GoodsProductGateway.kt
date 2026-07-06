package com.sportsapp.domain.message.gateway

/**
 * goods 도메인의 상품 소유자(판매자) id를 조회하는 게이트웨이 (BE-11, TDD FR-18).
 *
 * message 도메인은 goods 도메인을 직접 import 하지 않으며, 이 interface 를 통해서만
 * 상품 소유자 id 를 확인한다. 구현체는 infrastructure 레이어에 위치한다 — 외부 시스템 호출이
 * 아니라 같은 애플리케이션 내부의 다른 도메인 조회이므로 HTTP Client 는 관여하지 않는다.
 */
interface GoodsProductGateway {
    /**
     * @throws com.sportsapp.domain.common.exceptions.ResourceNotFoundException productId 에 해당하는 상품이 없을 때
     */
    fun findOwnerId(productId: Long): Long
}
