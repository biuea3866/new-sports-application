package com.sportsapp.infrastructure.persistence.post

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository

interface PostMongoRepository : MongoRepository<PostDocument, String> {
    fun findByUserId(userId: Long, pageable: Pageable): Page<PostDocument>
    fun findByType(type: String, pageable: Pageable): Page<PostDocument>
}
