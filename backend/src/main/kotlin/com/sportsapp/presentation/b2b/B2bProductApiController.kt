package com.sportsapp.presentation.b2b

import com.sportsapp.application.goods.ActivateMyProductCommand
import com.sportsapp.application.goods.ActivateMyProductUseCase
import com.sportsapp.application.goods.CreateMyProductUseCase
import com.sportsapp.application.goods.DeactivateMyProductCommand
import com.sportsapp.application.goods.DeactivateMyProductUseCase
import com.sportsapp.application.goods.ListMyProductsUseCase
import com.sportsapp.application.goods.MyProductResponse
import com.sportsapp.application.goods.ProductWithStockResponse
import com.sportsapp.application.goods.RestoreStockUseCase
import com.sportsapp.application.goods.UpdateMyProductUseCase
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/b2b/products")
class B2bProductApiController(
    private val createMyProductUseCase: CreateMyProductUseCase,
    private val updateMyProductUseCase: UpdateMyProductUseCase,
    private val activateMyProductUseCase: ActivateMyProductUseCase,
    private val deactivateMyProductUseCase: DeactivateMyProductUseCase,
    private val restoreStockUseCase: RestoreStockUseCase,
    private val listMyProductsUseCase: ListMyProductsUseCase,
) {
    @PostMapping
    @PreAuthorize("hasRole('GOODS_SELLER')")
    fun createProduct(
        @RequestBody request: CreateMyProductRequest,
    ): ResponseEntity<MyProductResponse> {
        val product = createMyProductUseCase.execute(request.toCommand())
        val response = MyProductResponse.of(product)
        return ResponseEntity.created(URI.create("/api/b2b/products/${response.id}")).body(response)
    }

    @GetMapping
    @PreAuthorize("hasRole('GOODS_SELLER')")
    fun listMyProducts(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<Page<ProductWithStockResponse>> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        return ResponseEntity.ok(listMyProductsUseCase.execute(pageable))
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('GOODS_SELLER')")
    fun updateProduct(
        @PathVariable id: Long,
        @RequestBody request: UpdateMyProductRequest,
    ): ResponseEntity<MyProductResponse> {
        val product = updateMyProductUseCase.execute(request.toCommand(id))
        return ResponseEntity.ok(MyProductResponse.of(product))
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('GOODS_SELLER')")
    fun activateProduct(@PathVariable id: Long): ResponseEntity<MyProductResponse> {
        val product = activateMyProductUseCase.execute(ActivateMyProductCommand(id))
        return ResponseEntity.ok(MyProductResponse.of(product))
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('GOODS_SELLER')")
    fun deactivateProduct(@PathVariable id: Long): ResponseEntity<MyProductResponse> {
        val product = deactivateMyProductUseCase.execute(DeactivateMyProductCommand(id))
        return ResponseEntity.ok(MyProductResponse.of(product))
    }

    @PostMapping("/{id}/stock/restore")
    @PreAuthorize("hasRole('GOODS_SELLER')")
    fun restoreStock(
        @PathVariable id: Long,
        @RequestBody request: RestoreStockRequest,
    ): ResponseEntity<Void> {
        restoreStockUseCase.execute(request.toCommand(id))
        return ResponseEntity.ok().build()
    }
}
