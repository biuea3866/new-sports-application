package com.sportsapp.infrastructure.goods.batch

import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.repository.ProductRepository
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter

/**
 * BE-11 청크 라이터 — 도메인 [ProductRepository] 인터페이스를 통해 청크 단위로 저장한다.
 * Step의 chunk-oriented 트랜잭션 경계 안에서 호출되므로, 청크(기본 500건)마다 별도 트랜잭션으로
 * 커밋되어 대량 단일 트랜잭션(테이블 전체 락 위험)을 만들지 않는다.
 */
class SellerTypeBackfillItemWriter(
    private val productRepository: ProductRepository,
) : ItemWriter<Product> {
    override fun write(chunk: Chunk<out Product>) {
        productRepository.saveAll(chunk.items)
    }
}
