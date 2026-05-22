package com.sportsapp.application.goods

import com.sportsapp.domain.goods.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetInventoryUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(ownerUserId: Long): InventoryResponse {
        val activeProductCount = goodsDomainService.countActiveProductsByOwnerId(ownerUserId)
        val outOfStockProductCount = goodsDomainService.countOutOfStockProductsByOwnerId(ownerUserId)
        return InventoryResponse(
            ownerUserId = ownerUserId,
            activeProductCount = activeProductCount,
            outOfStockProductCount = outOfStockProductCount,
        )
    }
}
