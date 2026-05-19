package com.sportsapp.domain.goods

class Stock(
    val productId: Long,
    var quantity: Int,
) {
    fun deduct(amount: Int) {
        if (quantity < amount) throw OutOfStockException(productId, amount, quantity)
        quantity -= amount
    }

    fun restore(amount: Int) {
        if (amount <= 0) throw InvalidQuantityException(amount)
        quantity += amount
    }
}
