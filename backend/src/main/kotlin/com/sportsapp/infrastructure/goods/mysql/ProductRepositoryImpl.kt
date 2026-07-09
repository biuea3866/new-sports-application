package com.sportsapp.infrastructure.goods.mysql

import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.repository.ProductRepository
import com.sportsapp.domain.goods.entity.ProductStatus
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
) : ProductRepository {

    override fun save(product: Product): Product = productJpaRepository.save(product)

    override fun saveAll(products: List<Product>): List<Product> = productJpaRepository.saveAll(products)

    override fun findById(id: Long): Product? = productJpaRepository.findByIdOrNull(id)

    override fun findByIdAndDeletedAtIsNull(id: Long): Product? =
        productJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findByCategoryAndStatus(category: ProductCategory, status: ProductStatus): List<Product> =
        productJpaRepository.findAllByCategoryAndStatus(category, status)

    override fun findByOwnerId(ownerId: Long): List<Product> =
        productJpaRepository.findAllByOwnerIdAndDeletedAtIsNull(ownerId)

    override fun countByOwnerIdAndStatus(ownerId: Long, status: ProductStatus): Long =
        productJpaRepository.countByOwnerIdAndStatusAndDeletedAtIsNull(ownerId, status)

    override fun countBySellerTypeIsNull(): Long = productJpaRepository.countBySellerTypeIsNull()
}
