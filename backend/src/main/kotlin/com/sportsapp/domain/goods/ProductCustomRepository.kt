package com.sportsapp.domain.goods

import java.math.BigDecimal
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ProductCustomRepository {
    fun search(
        category: ProductCategory?,
        keyword: String?,
        priceMin: BigDecimal?,
        priceMax: BigDecimal?,
        pageable: Pageable,
    ): Page<ProductWithStock>

    fun findByOwnerId(ownerId: Long, pageable: Pageable): Page<ProductWithStock>
}
