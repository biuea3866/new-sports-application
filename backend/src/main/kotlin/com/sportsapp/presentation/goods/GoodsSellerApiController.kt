package com.sportsapp.presentation.goods

import com.sportsapp.application.goods.ActivateMyProductUseCase
import com.sportsapp.application.goods.CreateMyProductUseCase
import com.sportsapp.application.goods.DeactivateMyProductUseCase
import com.sportsapp.application.goods.GetMyProductUseCase
import com.sportsapp.application.goods.ListMyProductsUseCase
import com.sportsapp.application.goods.ProductWithStockResponse
import com.sportsapp.application.goods.RestoreMyProductStockUseCase
import com.sportsapp.application.goods.UpdateMyProductUseCase
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/goods-seller/products")
@PreAuthorize("hasRole('GOODS_SELLER')")
class GoodsSellerApiController(
    private val createMyProductUseCase: CreateMyProductUseCase,
    private val listMyProductsUseCase: ListMyProductsUseCase,
    private val getMyProductUseCase: GetMyProductUseCase,
    private val updateMyProductUseCase: UpdateMyProductUseCase,
    private val activateMyProductUseCase: ActivateMyProductUseCase,
    private val deactivateMyProductUseCase: DeactivateMyProductUseCase,
    private val restoreMyProductStockUseCase: RestoreMyProductStockUseCase,
) {
    @PostMapping
    fun createProduct(
        @Valid @RequestBody request: CreateMyProductRequest,
    ): ResponseEntity<ProductWithStockResponse> {
        val response = createMyProductUseCase.execute(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping
    fun listMyProducts(
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Page<ProductWithStockResponse>> =
        ResponseEntity.ok(listMyProductsUseCase.execute(pageable))

    @GetMapping("/{id}")
    fun getMyProduct(
        @PathVariable id: Long,
    ): ResponseEntity<ProductWithStockResponse> =
        ResponseEntity.ok(getMyProductUseCase.execute(id))

    @PatchMapping("/{id}")
    fun updateMyProduct(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateMyProductRequest,
    ): ResponseEntity<ProductWithStockResponse> =
        ResponseEntity.ok(updateMyProductUseCase.execute(request.toCommand(id)))

    @PostMapping("/{id}/activate")
    fun activateMyProduct(
        @PathVariable id: Long,
    ): ResponseEntity<ProductWithStockResponse> =
        ResponseEntity.ok(activateMyProductUseCase.execute(id))

    @PostMapping("/{id}/deactivate")
    fun deactivateMyProduct(
        @PathVariable id: Long,
    ): ResponseEntity<ProductWithStockResponse> =
        ResponseEntity.ok(deactivateMyProductUseCase.execute(id))

    @PostMapping("/{id}/stock/restore")
    fun restoreMyProductStock(
        @PathVariable id: Long,
        @Valid @RequestBody request: RestoreMyProductStockRequest,
    ): ResponseEntity<ProductWithStockResponse> =
        ResponseEntity.ok(restoreMyProductStockUseCase.execute(request.toCommand(id)))
}
