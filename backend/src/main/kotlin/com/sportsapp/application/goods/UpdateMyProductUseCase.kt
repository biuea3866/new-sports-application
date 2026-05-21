package com.sportsapp.application.goods

import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.goods.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateMyProductUseCase(
    private val goodsDomainService: GoodsDomainService,
    private val ownershipGuard: OwnershipGuard,
) {
    @Transactional
    fun execute(command: UpdateMyProductCommand): ProductWithStockResponse {
        val ownerUserId = ownershipGuard.authUserId()
        return ProductWithStockResponse.of(
            goodsDomainService.updateProduct(
                productId = command.productId,
                ownerUserId = ownerUserId,
                name = command.name,
                category = command.category,
                price = command.price,
                description = command.description,
                imageUrl = command.imageUrl,
            )
        )
    }
}
