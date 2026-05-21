package com.sportsapp.application.goods

import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.goods.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RestoreStockUseCase(
    private val goodsDomainService: GoodsDomainService,
    private val ownershipGuard: OwnershipGuard,
) {
    @Transactional
    fun execute(command: RestoreStockCommand) {
        val authUserId = ownershipGuard.authUserId()
        goodsDomainService.restoreStockForOwner(command.productId, authUserId, command.quantity)
    }
}
