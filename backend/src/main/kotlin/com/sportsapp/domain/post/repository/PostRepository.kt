package com.sportsapp.domain.post.repository

import com.sportsapp.domain.post.entity.Post

interface PostRepository {
    fun save(post: Post): Post
    fun findById(id: Long): Post?
    fun findByUserId(userId: Long): List<Post>
}
