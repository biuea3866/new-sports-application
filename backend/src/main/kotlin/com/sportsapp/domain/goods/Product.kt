package com.sportsapp.domain.goods

import com.sportsapp.domain.common.JpaAuditingBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "products")
class Product(
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

    companion object {
        fun create(
            name: String,
            category: ProductCategory,
            price: BigDecimal,
            description: String,
            imageUrl: String,
            ownerUserId: Long,
        ): Product = Product(
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
