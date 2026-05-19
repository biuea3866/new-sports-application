package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductStatus
import com.sportsapp.infrastructure.persistence.ZonedDateTimeAttributeConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.ZonedDateTime

@Entity
@Table(name = "products")
class ProductEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,

    @Column(nullable = false, length = 255)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    val category: ProductCategory,

    @Column(nullable = false, precision = 12, scale = 2)
    val price: BigDecimal,

    @Column(columnDefinition = "TEXT")
    val description: String,

    @Column(length = 2048)
    val imageUrl: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ProductStatus,

    @Convert(converter = ZonedDateTimeAttributeConverter::class)
    @Column(nullable = false)
    val createdAt: ZonedDateTime,

    @Convert(converter = ZonedDateTimeAttributeConverter::class)
    @Column(nullable = false)
    var updatedAt: ZonedDateTime,
) {
    fun toDomain(): Product = Product(
        id = id,
        name = name,
        category = category,
        price = price,
        description = description,
        imageUrl = imageUrl,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(product: Product): ProductEntity = ProductEntity(
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
    }
}
