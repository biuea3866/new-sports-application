package com.sportsapp.application.goods

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeactivateMyProductUseCase(
    private val productRepository: ProductRepository,
    private val ownershipGuard: OwnershipGuard,
) {
    @Transactional
    fun execute(command: DeactivateMyProductCommand): Product {
        val authUserId = ownershipGuard.authUserId()
        val product = productRepository.findById(command.productId)
            ?: throw ResourceNotFoundException("Product", command.productId)
        ownershipGuard.requireOwned(product.ownerId, authUserId)
        product.deactivate()
        return productRepository.save(product)
    }
}
