package com.sportsapp.infrastructure.persistence.post

import com.sportsapp.domain.post.Comment
import org.springframework.data.jpa.repository.JpaRepository

interface CommentJpaRepository : JpaRepository<Comment, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Comment?
    fun findByPostIdAndDeletedAtIsNull(postId: Long): List<Comment>
}
