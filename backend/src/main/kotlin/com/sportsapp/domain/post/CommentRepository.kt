package com.sportsapp.domain.post

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface CommentRepository {
    fun save(comment: Comment): Comment
    fun saveAll(comments: List<Comment>): List<Comment>
    fun findById(id: Long): Comment?
    fun findByPostId(postId: Long): List<Comment>
    fun findAllActiveByPostId(postId: Long): List<Comment>
    fun findTop50ByPostId(postId: Long): List<Comment>
    fun findPageByPostId(postId: Long, pageable: Pageable): Page<Comment>
}
