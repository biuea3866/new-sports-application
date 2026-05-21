package com.sportsapp.application.goods

import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.goods.GoodsDomainService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListMyProductsUseCase(
    private val goodsDomainService: GoodsDomainService,
    private val ownershipGuard: OwnershipGuard,
) {
    @Transactional(readOnly = true)
    fun execute(pageable: Pageable): Page<ProductWithStockResponse> {
        val ownerUserId = ownershipGuard.authUserId()
        return goodsDomainService.listMyProducts(ownerUserId, pageable)
            .map { ProductWithStockResponse.of(it) }
    }
}
