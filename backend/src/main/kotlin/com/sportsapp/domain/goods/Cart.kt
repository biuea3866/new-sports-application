package com.sportsapp.domain.goods

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

    /**
     * UNIQUE(user_id, active_marker) 제약으로 활성 cart 단일성을 보장한다.
     * - 활성 상태: active_marker = 1 (상수 — user_id 당 1건만 허용)
     * - 비활성(soft-delete): active_marker = null (null 은 unique 체크 제외)
     *
     * CartRepositoryImpl.save() 에서 저장 직후 1L 로 설정하고,
     * soft-delete 처리 후 null 로 초기화한다.
     */
    @Column(name = "active_marker")
    var activeMarker: Long? = null
}
