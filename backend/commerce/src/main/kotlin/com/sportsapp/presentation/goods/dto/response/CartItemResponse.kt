package com.sportsapp.presentation.goods.dto.response

import com.sportsapp.domain.goods.vo.CartLineItem
import java.math.BigDecimal

/**
 * 장바구니 한 줄 응답.
 *
 * productId 만 내려 화면이 "상품 #121"을 렌더하던 회귀(유즈케이스 캡쳐 12-장바구니)를 막기 위해
 * 사람이 읽는 상품명과 단가·소계를 함께 내려준다.
 */
data class CartItemResponse(
    val id: Long,
    val productId: Long,
    val productName: String,
    val productImageUrl: String,
    val unitPrice: BigDecimal,
    val quantity: Int,
    val subtotal: BigDecimal,
) {
    companion object {
        fun of(lineItem: CartLineItem) = CartItemResponse(
            id = lineItem.id,
            productId = lineItem.productId,
            productName = lineItem.productName,
            productImageUrl = lineItem.productImageUrl,
            unitPrice = lineItem.unitPrice,
            quantity = lineItem.quantity,
            subtotal = lineItem.subtotal,
        )
    }
}
