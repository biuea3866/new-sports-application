package com.sportsapp.domain.goods

import java.math.BigDecimal
import java.time.ZonedDateTime

class Product(
    val id: Long,
    val name: String,
    val category: ProductCategory,
    val price: BigDecimal,
    val description: String,
    val imageUrl: String,
    var status: ProductStatus,
    val createdAt: ZonedDateTime,
    var updatedAt: ZonedDateTime,
) {
    fun activate() {
        check(status == ProductStatus.INACTIVE) { "이미 활성화된 상품입니다." }
        status = ProductStatus.ACTIVE
    }

    fun deactivate() {
        check(status == ProductStatus.ACTIVE) { "이미 비활성화된 상품입니다." }
        status = ProductStatus.INACTIVE
    }
}
