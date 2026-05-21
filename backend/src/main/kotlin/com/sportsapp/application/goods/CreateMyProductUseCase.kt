package com.sportsapp.application.goods

import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.goods.ProductWithStock
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateMyProductUseCase(
    private val goodsDomainService: GoodsDomainService,
    private val ownershipGuard: OwnershipGuard,
) {
    @Transactional
    fun execute(command: CreateMyProductCommand): ProductWithStockResponse {
        val ownerUserId = ownershipGuard.authUserId()
        val (product, stock) = goodsDomainService.createProduct(
            name = command.name,
            category = command.category,
            price = command.price,
            description = command.description,
            imageUrl = command.imageUrl,
            ownerUserId = ownerUserId,
        )
        return ProductWithStockResponse.of(ProductWithStock(product = product, stockQuantity = stock.quantity))
    }
}
