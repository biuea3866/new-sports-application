package com.sportsapp.infrastructure.persistence.post

import com.sportsapp.domain.post.Post
import org.springframework.data.jpa.repository.JpaRepository

interface PostJpaRepository : JpaRepository<Post, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Post?
    fun findByUserIdAndDeletedAtIsNull(userId: Long): List<Post>
}
