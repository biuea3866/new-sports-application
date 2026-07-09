package com.sportsapp.infrastructure.goods.mysql

import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.entity.ProductStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ProductJpaRepository : JpaRepository<Product, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Product?
    fun findAllByCategoryAndStatus(category: ProductCategory, status: ProductStatus): List<Product>
    fun findAllByOwnerIdAndDeletedAtIsNull(ownerId: Long): List<Product>
    fun countByOwnerIdAndStatusAndDeletedAtIsNull(ownerId: Long, status: ProductStatus): Long
    fun countBySellerTypeIsNull(): Long

    /**
     * BE-11 청크 리더 전용 — 항상 [pageable]의 페이지 0(오프셋 0)으로 호출해야 한다. 이 잡은
     * 읽은 행을 그 자리에서 WHERE 조건(seller_type IS NULL)에서 빠지게 만들므로, OFFSET을
     * 전진시키는 페이징(JpaPagingItemReader류)을 쓰면 청크 커밋 사이 결과 집합이 줄어들어
     * 다음 페이지가 아직 처리하지 않은 행을 건너뛴다([SellerTypeBackfillItemReader] 참조).
     */
    fun findBySellerTypeIsNull(pageable: Pageable): List<Product>
}
