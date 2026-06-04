package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.UpdateMyProductCommand
import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.service.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateMyProductUseCase(
    private val goodsDomainService: GoodsDomainService,
    private val ownershipGuard: OwnershipGuard,
) {
    @Transactional
    fun execute(command: UpdateMyProductCommand): ProductWithStock {
        val ownerUserId = ownershipGuard.authUserId()
        return goodsDomainService.updateProduct(
            productId = command.productId,
            ownerUserId = ownerUserId,
            name = command.name,
            category = command.category,
            price = command.price,
            description = command.description,
            imageUrl = command.imageUrl,
        )
    }
}
