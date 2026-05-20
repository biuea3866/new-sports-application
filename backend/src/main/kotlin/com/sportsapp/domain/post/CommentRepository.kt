package com.sportsapp.domain.post

interface CommentRepository {
    fun save(comment: Comment): Comment
    fun findById(id: Long): Comment?
    fun findByPostId(postId: Long): List<Comment>
}
