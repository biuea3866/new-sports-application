package com.sportsapp.domain.goods

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import java.math.BigDecimal
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GoodsDomainService(
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val customProductRepository: CustomProductRepository,
) {
    @Transactional(readOnly = true)
    fun search(
        category: ProductCategory?,
        keyword: String?,
        priceMin: BigDecimal?,
        priceMax: BigDecimal?,
        pageable: Pageable,
    ): Page<ProductWithStock> =
        customProductRepository.search(category, keyword, priceMin, priceMax, pageable)

    // TODO: UseCase 신설 시 @Transactional 을 UseCase 레이어로 이동. DomainService 에 @Transactional 유지는 UseCase 미존재 과도기 한정.
    @Transactional
    fun deductStock(productId: Long, quantity: Int) {
        productRepository.findById(productId) ?: throw ResourceNotFoundException("Product", productId)
        val stock = stockRepository.findByProductId(productId)
            ?: throw ResourceNotFoundException("Stock", productId)
        stock.deduct(quantity)
        stockRepository.save(stock)
    }

    @Transactional
    fun restoreStock(productId: Long, quantity: Int) {
        val stock = stockRepository.findByProductId(productId)
            ?: throw ResourceNotFoundException("Stock", productId)
        stock.restore(quantity)
        stockRepository.save(stock)
    }
}
