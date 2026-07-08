package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.dto.PopularProductSnapshot
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.goods.vo.ProductCategory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetPopularProductsUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(category: ProductCategory): List<PopularProductSnapshot> =
        goodsDomainService.getPopular(category)
}
