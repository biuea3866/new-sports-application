package com.sportsapp.domain.goods.dto

import com.sportsapp.domain.goods.entity.GoodsOrder

/**
 * order 통합조회(BE-08 예정)가 소비하는 GoodsOrder 읽기 프로젝션.
 *
 * [title]은 주문에 포함된 대표 상품명이다 — `goods_order_items`→`products` 조인으로 같은
 * goods 컨텍스트 안에서 구성한다(TDD "주문 표시명 확보 방식"). 항목이 여럿이면
 * "{대표 상품명} 외 {N-1}건"으로 표기하고, 참조 Product가 삭제·부재이면 빈 문자열로
 * 방어 반환한다(no-technical-item-name — 기술 식별자 대신 도메인 이름을 노출한다).
 */
data class GoodsOrderWithTitle(
    val order: GoodsOrder,
    val title: String,
)
