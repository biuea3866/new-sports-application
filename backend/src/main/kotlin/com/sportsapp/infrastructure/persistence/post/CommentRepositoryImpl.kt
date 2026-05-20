package com.sportsapp.infrastructure.persistence.post

import com.sportsapp.domain.post.Comment
import com.sportsapp.domain.post.CommentRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class CommentRepositoryImpl(
    private val commentJpaRepository: CommentJpaRepository,
) : CommentRepository {

    override fun save(comment: Comment): Comment = commentJpaRepository.save(comment)

    override fun findById(id: Long): Comment? = commentJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findByPostId(postId: Long): List<Comment> =
        commentJpaRepository.findByPostIdAndDeletedAtIsNull(postId)

    override fun findTop50ByPostId(postId: Long): List<Comment> =
        commentJpaRepository.findTop50ByPostIdAndDeletedAtIsNullOrderByCreatedAtAsc(postId)

    override fun findPageByPostId(postId: Long, pageable: Pageable): Page<Comment> =
        commentJpaRepository.findByPostIdAndDeletedAtIsNull(postId, pageable)
}
