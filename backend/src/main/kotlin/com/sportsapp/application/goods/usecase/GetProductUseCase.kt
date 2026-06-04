package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.service.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetProductUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(productId: Long): ProductWithStock =
        goodsDomainService.getProductWithStock(productId)
}
