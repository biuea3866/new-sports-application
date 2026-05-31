package com.sportsapp.domain.goods

import java.math.BigDecimal

internal fun testProduct(ownerId: Long = 1L): Product = Product(
    name = "테스트 상품",
    category = ProductCategory.EQUIPMENT,
    price = BigDecimal("10000"),
    description = "테스트용",
    imageUrl = "https://example.com/test.jpg",
    status = ProductStatus.ACTIVE,
    ownerId = ownerId,
)

internal fun testStock(quantity: Int): Stock = Stock(productId = 0L, quantity = quantity)
