package com.sportsapp.infrastructure.persistence.post

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository

interface CommentMongoRepository : MongoRepository<CommentDocument, String> {
    fun findByPostId(postId: String, pageable: Pageable): Page<CommentDocument>
    fun findByUserId(userId: Long): List<CommentDocument>
}
