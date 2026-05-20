package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.domain.goods.Cart
import org.springframework.data.jpa.repository.JpaRepository

interface CartJpaRepository : JpaRepository<Cart, Long> {
    fun findByUserId(userId: Long): Cart?
}
