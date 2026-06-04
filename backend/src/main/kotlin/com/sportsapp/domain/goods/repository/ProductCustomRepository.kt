package com.sportsapp.domain.goods.repository

import java.math.BigDecimal
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.vo.ProductCategory

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
