package com.sportsapp.domain.goods.dto

import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.GoodsOrderItem

/**
 * 주문 상세 조회(`GET /goods-orders/{orderId}`)용 읽기 프로젝션.
 *
 * 통합 주문내역 리스트가 소비하는 [GoodsOrderWithTitle]과 동일하게 대표 상품명([title])을
 * 포함해, 상세 응답이 리스트보다 빈약해지는 역전을 막는다(Option A+). [title]은 리스트와
 * 동일한 소스·동일한 조합 방식(goods_order_items→products 조인, 다건 시 "외 N건")으로
 * 채운다.
 */
data class GoodsOrderDetail(
    val order: GoodsOrder,
    val items: List<GoodsOrderItem>,
    val title: String,
)
