package com.sportsapp.application.goods

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.goods.ProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RestoreStockUseCase(
    private val goodsDomainService: GoodsDomainService,
    private val productRepository: ProductRepository,
    private val ownershipGuard: OwnershipGuard,
) {
    @Transactional
    fun execute(command: RestoreStockCommand) {
        val authUserId = ownershipGuard.authUserId()
        val product = productRepository.findById(command.productId)
            ?: throw ResourceNotFoundException("Product", command.productId)
        ownershipGuard.requireOwned(product.ownerId, authUserId)
        goodsDomainService.restoreStock(command.productId, command.quantity)
    }
}
