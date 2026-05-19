package com.sportsapp.domain.post

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface CommentRepository {
    fun save(comment: Comment): Comment
    fun findById(id: String): Comment?
    fun findByPostId(postId: String, pageable: Pageable): Page<Comment>
    fun delete(id: String)
}
