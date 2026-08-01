package com.sportsapp.domain.goods.vo

import java.math.BigDecimal

/**
 * 장바구니 한 줄 — 담긴 수량에 상품(이름·단가·이미지)을 결합한 읽기 모델.
 *
 * 장바구니 응답이 productId·quantity 만 담아 화면이 "상품 #121"을 렌더하던 회귀
 * (유즈케이스 캡쳐 12-장바구니)를 막기 위한 타입이다. 같은 goods 컨텍스트의 Product 를
 * 조인해 만들며, 소계는 보관하지 않고 단가 × 수량으로 파생한다.
 */
data class CartLineItem(
    val id: Long,
    val productId: Long,
    val productName: String,
    val productImageUrl: String,
    val unitPrice: BigDecimal,
    val quantity: Int,
) {
    val subtotal: BigDecimal
        get() = unitPrice.multiply(BigDecimal.valueOf(quantity.toLong()))
}
