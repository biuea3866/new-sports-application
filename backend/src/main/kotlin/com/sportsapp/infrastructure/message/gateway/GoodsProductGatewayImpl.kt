package com.sportsapp.infrastructure.message.gateway

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.goods.repository.ProductRepository
import com.sportsapp.domain.message.gateway.GoodsProductGateway
import org.springframework.stereotype.Component

/**
 * `GoodsProductGateway` 구현체 (BE-11, TDD FR-18).
 *
 * goods 도메인의 `ProductRepository`로 `Product.ownerId`를 조회한다. `FacilityOwnershipGatewayImpl`
 * (booking -> facility)과 동일한 크로스 도메인 내부 조회 게이트웨이 패턴 — 외부 시스템 Client 가 아니다.
 */
@Component
class GoodsProductGatewayImpl(
    private val productRepository: ProductRepository,
) : GoodsProductGateway {

    override fun findOwnerId(productId: Long): Long {
        val product = productRepository.findByIdAndDeletedAtIsNull(productId)
            ?: throw ResourceNotFoundException("Product", productId)
        return product.ownerId
    }
}
