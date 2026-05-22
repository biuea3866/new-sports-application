package com.sportsapp.application.goods

import com.sportsapp.domain.goods.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetGoodsSalesUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(ownerUserId: Long): GoodsSalesResponse {
        val activeProductCount = goodsDomainService.countActiveProductsByOwnerId(ownerUserId)
        val outOfStockProductCount = goodsDomainService.countOutOfStockProductsByOwnerId(ownerUserId)
        val confirmedOrderCount = goodsDomainService.countConfirmedOrdersByOwnerUserId(ownerUserId)
        val totalRevenue = goodsDomainService.sumRevenueByOwnerUserId(ownerUserId)
        return GoodsSalesResponse(
            ownerUserId = ownerUserId,
            activeProductCount = activeProductCount,
            outOfStockProductCount = outOfStockProductCount,
            confirmedOrderCount = confirmedOrderCount,
            totalRevenue = totalRevenue,
        )
    }
}
