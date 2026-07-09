package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.ProductCriteria
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.exception.InvalidPriceRangeException
import com.sportsapp.domain.goods.service.GoodsDomainService
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SearchProductsUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(criteria: ProductCriteria): Page<ProductWithStock> {
        criteria.validatePriceRange()
        val pageable = criteria.toPageable()
        return goodsDomainService.search(
            category = criteria.category,
            keyword = criteria.keyword,
            priceMin = criteria.priceMin,
            priceMax = criteria.priceMax,
            sellerType = null,
            pageable = pageable,
        )
    }
}
