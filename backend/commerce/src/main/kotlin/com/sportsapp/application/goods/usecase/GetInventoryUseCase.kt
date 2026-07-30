package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.InventoryResult
import com.sportsapp.domain.goods.service.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetInventoryUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(ownerUserId: Long): InventoryResult {
        val activeProductCount = goodsDomainService.countActiveProductsByOwnerId(ownerUserId)
        val outOfStockProductCount = goodsDomainService.countOutOfStockProductsByOwnerId(ownerUserId)
        return InventoryResult(
            ownerUserId = ownerUserId,
            activeProductCount = activeProductCount,
            outOfStockProductCount = outOfStockProductCount,
        )
    }
}
