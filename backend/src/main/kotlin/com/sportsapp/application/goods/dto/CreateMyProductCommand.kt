package com.sportsapp.application.goods.dto

import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.vo.SellerType
import java.math.BigDecimal

data class CreateMyProductCommand(
    val name: String,
    val category: ProductCategory,
    val price: BigDecimal,
    val description: String,
    val imageUrl: String,
    /**
     * ADR-003 계약 진화용 필드(BE-03) — 등록 경로의 실제 sellerType 판별은 요청 컨트롤러가 아니라
     * [com.sportsapp.domain.goods.service.GoodsDomainService.createProduct]가
     * `AuthChannelResolver.isPartnerAuthenticated()`로 직접 수행한다(no-external-state-check,
     * 판별 로직을 domain에 둔다). 현재 쓰기 경로에서는 이 값이 사용되지 않는다 — 향후 명시적
     * override가 필요해지는 시나리오(예: 운영 도구)를 위해 계약 형태만 예약해 둔다.
     */
    val sellerType: SellerType? = null,
)
