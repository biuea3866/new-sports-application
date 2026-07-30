package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.service.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ActivateMyProductUseCase(
    private val goodsDomainService: GoodsDomainService,
    private val ownershipGuard: OwnershipGuard,
) {
    @Transactional
    fun execute(productId: Long): ProductWithStock {
        val ownerUserId = ownershipGuard.authUserId()
        return goodsDomainService.activateProductWithStock(productId, ownerUserId)
    }
}
