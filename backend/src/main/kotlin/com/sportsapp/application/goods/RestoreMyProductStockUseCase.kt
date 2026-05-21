package com.sportsapp.application.goods

import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.goods.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RestoreMyProductStockUseCase(
    private val goodsDomainService: GoodsDomainService,
    private val ownershipGuard: OwnershipGuard,
) {
    @Transactional
    fun execute(command: RestoreMyProductStockCommand): ProductWithStockResponse {
        val ownerUserId = ownershipGuard.authUserId()
        goodsDomainService.restoreProductStock(command.productId, ownerUserId, command.quantity)
        return ProductWithStockResponse.of(
            goodsDomainService.getProductByIdAndOwnerId(command.productId, ownerUserId)
        )
    }
}
