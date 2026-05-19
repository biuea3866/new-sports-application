package com.sportsapp.domain.post

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface PostRepository {
    fun save(post: Post): Post
    fun findById(id: String): Post?
    fun findByUserId(userId: Long, pageable: Pageable): Page<Post>
    fun findByType(type: PostType, pageable: Pageable): Page<Post>
    fun delete(id: String)
}
