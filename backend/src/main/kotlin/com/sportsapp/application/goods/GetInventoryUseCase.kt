package com.sportsapp.application.goods

import com.sportsapp.domain.goods.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetInventoryUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: GetInventoryCommand): InventoryResponse {
        val items = goodsDomainService.findInventory(
            ownerUserId = command.operatorUserId,
            lowStockOnly = command.lowStockOnly,
        )
        return InventoryResponse.of(items)
    }
}
