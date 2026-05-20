package com.sportsapp.application.goods

import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.goods.ProductCategory
import org.springframework.stereotype.Service

@Service
class InvalidateCacheUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    fun execute(category: ProductCategory) {
        goodsDomainService.invalidatePopularCache(category)
    }
}
