package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.CreateMyProductCommand
import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.entity.Stock
import com.sportsapp.domain.goods.service.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateMyProductUseCase(
    private val goodsDomainService: GoodsDomainService,
    private val ownershipGuard: OwnershipGuard,
) {
    @Transactional
    fun execute(command: CreateMyProductCommand): ProductWithStock {
        val ownerUserId = ownershipGuard.authUserId()
        val (product, stock) = goodsDomainService.createProduct(
            name = command.name,
            category = command.category,
            price = command.price,
            description = command.description,
            imageUrl = command.imageUrl,
            ownerUserId = ownerUserId,
        )
        return ProductWithStock(product = product, stockQuantity = stock.quantity)
    }
}
