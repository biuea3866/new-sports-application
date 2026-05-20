package com.sportsapp.application.goods

import com.sportsapp.domain.goods.GoodsDomainService
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SearchProductsUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(criteria: ProductCriteria): Page<ProductWithStockResponse> {
        validatePriceRange(criteria)
        val pageable = criteria.toPageable()
        return goodsDomainService.search(
            category = criteria.category,
            keyword = criteria.keyword,
            priceMin = criteria.priceMin,
            priceMax = criteria.priceMax,
            pageable = pageable,
        ).map { ProductWithStockResponse.of(it) }
    }

    private fun validatePriceRange(criteria: ProductCriteria) {
        val priceMin = criteria.priceMin ?: return
        val priceMax = criteria.priceMax ?: return
        if (priceMin > priceMax) throw InvalidPriceRangeException(priceMin, priceMax)
    }
}
