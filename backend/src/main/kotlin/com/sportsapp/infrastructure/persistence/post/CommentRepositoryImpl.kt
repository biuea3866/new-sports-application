package com.sportsapp.infrastructure.persistence.post

import com.sportsapp.domain.post.Comment
import com.sportsapp.domain.post.CommentRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class CommentRepositoryImpl(
    private val commentMongoRepository: CommentMongoRepository,
) : CommentRepository {

    override fun save(comment: Comment): Comment =
        commentMongoRepository.save(CommentDocument.fromDomain(comment)).toDomain()

    override fun findById(id: String): Comment? =
        commentMongoRepository.findById(id).orElse(null)?.toDomain()

    override fun findByPostId(postId: String, pageable: Pageable): Page<Comment> =
        commentMongoRepository.findByPostId(postId, pageable).map { it.toDomain() }

    override fun delete(id: String) =
        commentMongoRepository.deleteById(id)
}
