package com.sportsapp.presentation.goods

import com.sportsapp.application.goods.GetPopularProductsUseCase
import com.sportsapp.application.goods.PopularProductResponse
import com.sportsapp.application.goods.ProductCriteria
import com.sportsapp.application.goods.ProductWithStockResponse
import com.sportsapp.application.goods.SearchProductsUseCase
import com.sportsapp.domain.goods.ProductCategory
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/products")
@Profile("!test-jpa")
class ProductApiController(
    private val searchProductsUseCase: SearchProductsUseCase,
    private val getPopularProductsUseCase: GetPopularProductsUseCase,
) {
    @GetMapping
    fun searchProducts(
        @RequestParam(required = false) category: ProductCategory?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) priceMin: BigDecimal?,
        @RequestParam(required = false) priceMax: BigDecimal?,
        @RequestParam(defaultValue = "recent") sort: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<Page<ProductWithStockResponse>> {
        val criteria = ProductCriteria(
            category = category,
            keyword = keyword,
            priceMin = priceMin,
            priceMax = priceMax,
            sort = sort,
            page = page,
            size = size,
        )
        return ResponseEntity.ok(searchProductsUseCase.execute(criteria))
    }

    @GetMapping("/popular")
    fun getPopularProducts(
        @RequestParam category: ProductCategory,
    ): ResponseEntity<List<PopularProductResponse>> =
        ResponseEntity.ok(getPopularProductsUseCase.execute(category))
}
