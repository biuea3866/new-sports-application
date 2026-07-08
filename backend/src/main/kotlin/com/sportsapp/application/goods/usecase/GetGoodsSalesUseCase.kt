package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.GoodsSalesResult
import com.sportsapp.domain.goods.service.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetGoodsSalesUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(ownerUserId: Long): GoodsSalesResult {
        val activeProductCount = goodsDomainService.countActiveProductsByOwnerId(ownerUserId)
        val outOfStockProductCount = goodsDomainService.countOutOfStockProductsByOwnerId(ownerUserId)
        val confirmedOrderCount = goodsDomainService.countConfirmedOrdersByOwnerUserId(ownerUserId)
        val totalRevenue = goodsDomainService.sumRevenueByOwnerUserId(ownerUserId)
        return GoodsSalesResult(
            ownerUserId = ownerUserId,
            activeProductCount = activeProductCount,
            outOfStockProductCount = outOfStockProductCount,
            confirmedOrderCount = confirmedOrderCount,
            totalRevenue = totalRevenue,
        )
    }
}
