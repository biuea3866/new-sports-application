package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.goods.vo.ProductCategory
import org.springframework.stereotype.Service

@Service
class InvalidateCacheUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    fun execute(category: ProductCategory) {
        goodsDomainService.invalidatePopularCache(category)
    }
}
