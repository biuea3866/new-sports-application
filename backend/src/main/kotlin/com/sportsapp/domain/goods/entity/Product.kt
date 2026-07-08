package com.sportsapp.domain.goods.entity

import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import com.sportsapp.domain.goods.exception.ProductInactiveException
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.entity.ProductStatus

@Entity
@Table(name = "products")
class Product(
    @Column(nullable = false, length = 255)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var category: ProductCategory,

    @Column(nullable = false, precision = 12, scale = 2)
    var price: BigDecimal,

    @Column(columnDefinition = "TEXT")
    var description: String,

    @Column(length = 2048)
    var imageUrl: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ProductStatus,

    @Column(name = "owner_id", nullable = false)
    val ownerId: Long,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    fun activate() {
        check(status == ProductStatus.INACTIVE) { "이미 활성화된 상품입니다." }
        status = ProductStatus.ACTIVE
    }

    fun deactivate() {
        check(status == ProductStatus.ACTIVE) { "이미 비활성화된 상품입니다." }
        status = ProductStatus.INACTIVE
    }

    fun requireActive() {
        if (status != ProductStatus.ACTIVE) throw ProductInactiveException(id)
    }

    fun requireOwnedBy(ownerUserId: Long) {
        if (ownerId != ownerUserId) throw ResourceNotFoundException("Product", id)
    }

    fun update(
        name: String?,
        category: ProductCategory?,
        price: BigDecimal?,
        description: String?,
        imageUrl: String?,
    ) {
        name?.let {
            require(it.isNotBlank()) { "name must not be blank" }
            this.name = it
        }
        category?.let { this.category = it }
        price?.let {
            require(it > BigDecimal.ZERO) { "price must be positive" }
            this.price = it
        }
        description?.let { this.description = it }
        imageUrl?.let { this.imageUrl = it }
    }

    companion object {
        fun create(
            name: String,
            category: ProductCategory,
            price: BigDecimal,
            description: String,
            imageUrl: String,
            ownerUserId: Long,
        ): Product {
            require(ownerUserId > 0) { "ownerUserId must be positive" }
            return Product(
                name = name,
                category = category,
                price = price,
                description = description,
                imageUrl = imageUrl,
                status = ProductStatus.INACTIVE,
                ownerId = ownerUserId,
            )
        }
    }
}
