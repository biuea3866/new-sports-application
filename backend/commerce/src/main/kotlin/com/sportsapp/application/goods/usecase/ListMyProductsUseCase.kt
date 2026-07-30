package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.service.GoodsDomainService
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
    fun execute(pageable: Pageable): Page<ProductWithStock> {
        val ownerUserId = ownershipGuard.authUserId()
        return goodsDomainService.listMyProducts(ownerUserId, pageable)
    }
}
