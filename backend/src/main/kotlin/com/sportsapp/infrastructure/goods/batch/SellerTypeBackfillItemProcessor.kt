package com.sportsapp.infrastructure.goods.batch

import com.sportsapp.domain.goods.entity.Product
import org.springframework.batch.item.ItemProcessor

/**
 * BE-11 청크 프로세서 — 엔티티 캡슐화 메서드([Product.assignDefaultSellerTypeIfMissing])에 위임한다
 * (no-external-state-check: 필드를 직접 대입하지 않는다).
 */
class SellerTypeBackfillItemProcessor : ItemProcessor<Product, Product> {
    override fun process(item: Product): Product {
        item.assignDefaultSellerTypeIfMissing()
        return item
    }
}
