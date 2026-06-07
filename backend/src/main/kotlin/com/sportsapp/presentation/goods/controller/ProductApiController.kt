package com.sportsapp.presentation.goods.controller

import com.sportsapp.application.goods.dto.ProductCriteria
import com.sportsapp.application.goods.usecase.GetPopularProductsUseCase
import com.sportsapp.application.goods.usecase.GetProductUseCase
import com.sportsapp.application.goods.usecase.SearchProductsUseCase
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.presentation.goods.dto.response.PopularProductResponse
import com.sportsapp.presentation.goods.dto.response.ProductWithStockResponse
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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
    private val getProductUseCase: GetProductUseCase,
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
        return ResponseEntity.ok(searchProductsUseCase.execute(criteria).map { ProductWithStockResponse.of(it) })
    }

    @GetMapping("/{id}")
    fun getProduct(
        @PathVariable id: Long,
    ): ResponseEntity<ProductWithStockResponse> =
        ResponseEntity.ok(ProductWithStockResponse.of(getProductUseCase.execute(id)))

    @GetMapping("/popular")
    fun getPopularProducts(
        @RequestParam category: ProductCategory,
    ): ResponseEntity<List<PopularProductResponse>> =
        ResponseEntity.ok(getPopularProductsUseCase.execute(category).map { PopularProductResponse.of(it) })
}
