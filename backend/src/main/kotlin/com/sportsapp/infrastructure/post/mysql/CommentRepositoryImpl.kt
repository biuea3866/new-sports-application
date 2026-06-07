package com.sportsapp.infrastructure.post.mysql

import com.sportsapp.domain.post.entity.Comment
import com.sportsapp.domain.post.repository.CommentRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class CommentRepositoryImpl(
    private val commentJpaRepository: CommentJpaRepository,
) : CommentRepository {

    override fun save(comment: Comment): Comment = commentJpaRepository.save(comment)

    override fun saveAll(comments: List<Comment>): List<Comment> = commentJpaRepository.saveAll(comments)

    override fun findById(id: Long): Comment? = commentJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findByPostId(postId: Long): List<Comment> =
        commentJpaRepository.findByPost_IdAndDeletedAtIsNull(postId)

    override fun findAllActiveByPostId(postId: Long): List<Comment> =
        commentJpaRepository.findAllByPost_IdAndDeletedAtIsNull(postId)

    override fun findTop50ByPostId(postId: Long): List<Comment> =
        commentJpaRepository.findTop50ByPost_IdAndDeletedAtIsNullOrderByCreatedAtAsc(postId)

    override fun findPageByPostId(postId: Long, pageable: Pageable): Page<Comment> =
        commentJpaRepository.findByPost_IdAndDeletedAtIsNull(postId, pageable)
}
