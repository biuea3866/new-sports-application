package com.sportsapp.application.goods

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateMyProductUseCase(
    private val productRepository: ProductRepository,
    private val ownershipGuard: OwnershipGuard,
) {
    @Transactional
    fun execute(command: UpdateMyProductCommand): Product {
        val authUserId = ownershipGuard.authUserId()
        val product = productRepository.findById(command.productId)
            ?: throw ResourceNotFoundException("Product", command.productId)
        ownershipGuard.requireOwned(product.ownerId, authUserId)
        product.updateMeta(
            name = command.name,
            category = command.category,
            price = command.price,
            description = command.description,
            imageUrl = command.imageUrl,
        )
        return productRepository.save(product)
    }
}
