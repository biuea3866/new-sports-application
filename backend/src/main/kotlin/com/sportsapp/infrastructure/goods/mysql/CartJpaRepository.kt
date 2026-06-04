package com.sportsapp.infrastructure.goods.mysql

import com.sportsapp.domain.goods.entity.Cart
import org.springframework.data.jpa.repository.JpaRepository

interface CartJpaRepository : JpaRepository<Cart, Long> {
    fun findByUserIdAndDeletedAtIsNull(userId: Long): Cart?
}
