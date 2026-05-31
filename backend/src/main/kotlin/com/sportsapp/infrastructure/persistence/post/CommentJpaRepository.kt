package com.sportsapp.infrastructure.persistence.post

import com.sportsapp.domain.post.Comment
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface CommentJpaRepository : JpaRepository<Comment, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Comment?
    fun findByPostIdAndDeletedAtIsNull(postId: Long): List<Comment>
    fun findTop50ByPostIdAndDeletedAtIsNullOrderByCreatedAtAsc(postId: Long): List<Comment>
    fun findByPostIdAndDeletedAtIsNull(postId: Long, pageable: Pageable): Page<Comment>
    fun findAllByPostIdAndDeletedAtIsNull(postId: Long): List<Comment>
}
