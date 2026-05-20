package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductRepository
import com.sportsapp.domain.goods.ProductStatus
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
) : ProductRepository {

    override fun save(product: Product): Product = productJpaRepository.save(product)

    override fun findById(id: Long): Product? = productJpaRepository.findByIdOrNull(id)

    override fun findByCategoryAndStatus(category: ProductCategory, status: ProductStatus): List<Product> =
        productJpaRepository.findAllByCategoryAndStatus(category, status)

    override fun findByOwnerId(ownerId: Long): List<Product> =
        productJpaRepository.findAllByOwnerIdAndDeletedAtIsNull(ownerId)
}
