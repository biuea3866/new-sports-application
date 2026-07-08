package com.sportsapp.domain.goods.entity

import com.sportsapp.domain.common.JpaAuditingBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "carts")
class Cart(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    // UNIQUE(user_id, active_marker) 보조 컬럼 — 활성 cart 는 1, soft-delete 시 NULL.
    // 동시 INSERT 시 DB unique 가 중복 활성 cart 를 차단(최종 방어선)한다.
    @Column(name = "active_marker")
    var activeMarker: Long? = 1
        protected set

    // soft-delete 시 active_marker 를 NULL 로 풀어 같은 user 의 새 활성 cart 생성이 unique 에 막히지 않게 한다.
    override fun softDelete(userId: Long?) {
        super.softDelete(userId)
        activeMarker = null
    }
}
