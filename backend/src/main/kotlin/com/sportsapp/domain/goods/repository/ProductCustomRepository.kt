package com.sportsapp.domain.goods.repository

import java.math.BigDecimal
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.vo.SellerType

interface ProductCustomRepository {
    /** status=ACTIVE만 대상(공개 검색). [sellerType]은 옵션 필터(BE-03, catalog 재사용). */
    fun search(
        category: ProductCategory?,
        keyword: String?,
        priceMin: BigDecimal?,
        priceMax: BigDecimal?,
        sellerType: SellerType?,
        pageable: Pageable,
    ): Page<ProductWithStock>

    fun findByOwnerId(ownerId: Long, pageable: Pageable): Page<ProductWithStock>
}
