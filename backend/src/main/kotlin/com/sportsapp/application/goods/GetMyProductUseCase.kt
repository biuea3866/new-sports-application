package com.sportsapp.application.goods

import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.goods.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetMyProductUseCase(
    private val goodsDomainService: GoodsDomainService,
    private val ownershipGuard: OwnershipGuard,
) {
    @Transactional(readOnly = true)
    fun execute(productId: Long): ProductWithStockResponse {
        val ownerUserId = ownershipGuard.authUserId()
        return ProductWithStockResponse.of(
            goodsDomainService.getProductByIdAndOwnerId(productId, ownerUserId)
        )
    }
}
