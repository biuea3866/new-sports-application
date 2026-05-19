package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.domain.goods.Stock
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version

@Entity
@Table(name = "stocks")
class StockEntity(
    @Id
    val productId: Long,

    @Column(nullable = false)
    var quantity: Int,

    @Version
    val version: Long,
) {
    fun toDomain(): Stock = Stock(
        productId = productId,
        quantity = quantity,
    )
}
