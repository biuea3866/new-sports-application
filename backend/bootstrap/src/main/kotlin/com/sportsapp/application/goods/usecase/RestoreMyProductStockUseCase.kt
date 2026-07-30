package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.RestoreMyProductStockCommand
import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.service.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RestoreMyProductStockUseCase(
    private val goodsDomainService: GoodsDomainService,
    private val ownershipGuard: OwnershipGuard,
) {
    @Transactional
    fun execute(command: RestoreMyProductStockCommand): ProductWithStock {
        val ownerUserId = ownershipGuard.authUserId()
        goodsDomainService.restoreProductStock(command.productId, ownerUserId, command.quantity)
        return goodsDomainService.getProductByIdAndOwnerId(command.productId, ownerUserId)
    }
}
