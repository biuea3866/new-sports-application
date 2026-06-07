package com.sportsapp.infrastructure.post.mysql

import com.sportsapp.domain.post.entity.Post
import org.springframework.data.jpa.repository.JpaRepository

interface PostJpaRepository : JpaRepository<Post, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Post?
    fun findByUserIdAndDeletedAtIsNull(userId: Long): List<Post>
}
