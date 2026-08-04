package com.sportsapp.domain.goods.entity

import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import com.sportsapp.domain.goods.exception.ProductInactiveException
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.vo.SellerType
import com.sportsapp.domain.goods.entity.ProductStatus

@Entity
@Table(name = "products")
class Product(
    @Column(nullable = false, length = 255)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var category: ProductCategory,

    @Column(nullable = false, precision = 12, scale = 2)
    var price: BigDecimal,

    @Column(columnDefinition = "TEXT")
    var description: String,

    @Column(length = 2048)
    var imageUrl: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ProductStatus,

    /**
     * 판매자 유형(B2C/B2B). `seller_type` 컬럼은 **V62 로 NOT NULL 이 됐고**(expand-contract contract
     * 완료, 백필 잡도 제거) 실 데이터에는 null 이 없다. 이 프로퍼티가 아직 nullable 인 것은 원시
     * 생성자를 쓰는 기존 테스트 픽스처 호환 때문이며, 타입 조임은 후속 과제다
     * (`MSA 물리분리/후속-리스크-등록부.md` R-26). 신규 등록은 [create]가 항상 명시값을 요구한다.
     *
     * 원시 생성자의 기본값(B2C)은 sellerType과 무관한 기존 테스트 픽스처(goods 밖 30여개 파일)의
     * 컴파일 호환을 위한 실용적 예외다 — no-default-constructor-values 원칙은 실제 쓰기 경로인
     * [create] 팩토리(기본값 없음, 호출부 명시 전달 강제)에서 지킨다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "seller_type", length = 10)
    var sellerType: SellerType? = SellerType.B2C,

    @Column(name = "owner_id", nullable = false)
    val ownerId: Long,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    fun activate() {
        check(status == ProductStatus.INACTIVE) { "이미 활성화된 상품입니다." }
        status = ProductStatus.ACTIVE
    }

    fun deactivate() {
        check(status == ProductStatus.ACTIVE) { "이미 비활성화된 상품입니다." }
        status = ProductStatus.INACTIVE
    }

    fun requireActive() {
        if (status != ProductStatus.ACTIVE) throw ProductInactiveException(id)
    }

    fun requireOwnedBy(ownerUserId: Long) {
        if (ownerId != ownerUserId) throw ResourceNotFoundException("Product", id)
    }

    fun update(
        name: String?,
        category: ProductCategory?,
        price: BigDecimal?,
        description: String?,
        imageUrl: String?,
    ) {
        name?.let {
            require(it.isNotBlank()) { "name must not be blank" }
            this.name = it
        }
        category?.let { this.category = it }
        price?.let {
            require(it > BigDecimal.ZERO) { "price must be positive" }
            this.price = it
        }
        description?.let { this.description = it }
        imageUrl?.let { this.imageUrl = it }
    }

    companion object {
        fun create(
            name: String,
            category: ProductCategory,
            price: BigDecimal,
            description: String,
            imageUrl: String,
            ownerUserId: Long,
            sellerType: SellerType,
        ): Product {
            require(ownerUserId > 0) { "ownerUserId must be positive" }
            return Product(
                name = name,
                category = category,
                price = price,
                description = description,
                imageUrl = imageUrl,
                status = ProductStatus.INACTIVE,
                sellerType = sellerType,
                ownerId = ownerUserId,
            )
        }
    }
}
