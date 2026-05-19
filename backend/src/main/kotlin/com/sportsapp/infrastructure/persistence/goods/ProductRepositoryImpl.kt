package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductRepository
import com.sportsapp.domain.goods.ProductStatus
import org.springframework.stereotype.Component

@Component
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
) : ProductRepository {

    override fun save(product: Product): Product {
        val entity = ProductEntity(
            id = product.id,
            name = product.name,
            category = product.category,
            price = product.price,
            description = product.description,
            imageUrl = product.imageUrl,
            status = product.status,
            createdAt = product.createdAt,
            updatedAt = product.updatedAt,
        )
        return productJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): Product? =
        productJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findByCategoryAndStatus(category: ProductCategory, status: ProductStatus): List<Product> =
        productJpaRepository.findAllByCategoryAndStatus(category, status).map { it.toDomain() }

    override fun deleteAll() = productJpaRepository.deleteAll()
}
