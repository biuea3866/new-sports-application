package com.sportsapp.application.goods

import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.goods.ProductCategory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetPopularProductsUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(category: ProductCategory): List<PopularProductResponse> =
        goodsDomainService.getPopular(category).map(PopularProductResponse::of)

    companion object {
        const val POPULAR_LIMIT = 20
    }
}
