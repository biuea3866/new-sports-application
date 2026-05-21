package com.sportsapp.domain.post

interface PostRepository {
    fun save(post: Post): Post
    fun findById(id: Long): Post?
    fun findByUserId(userId: Long): List<Post>
}
