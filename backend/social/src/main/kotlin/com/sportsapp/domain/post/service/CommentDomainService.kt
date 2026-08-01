package com.sportsapp.domain.post.service

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.post.entity.Comment
import com.sportsapp.domain.post.repository.CommentRepository
import com.sportsapp.domain.post.repository.PostRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

/**
 * Post 에 딸린 댓글 책임을 분리한 도메인 서비스 (PostDomainService TooManyFunctions 정리, W1-DEBT-01).
 * 댓글 CRUD·조회는 여기서, Post 자체의 생성·수정·조회는 [PostDomainService]가 담당한다.
 */
@Service
class CommentDomainService(
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
) {
    fun addComment(postId: Long, userId: Long, content: String): Comment {
        val post = postRepository.findById(postId)
            ?: throw ResourceNotFoundException("Post", postId)
        val comment = post.addComment(userId = userId, content = content)
        return commentRepository.save(comment)
    }

    fun deleteComment(commentId: Long, requestUserId: Long) {
        val comment = commentRepository.findById(commentId)
            ?: throw ResourceNotFoundException("Comment", commentId)
        comment.delete(requestUserId)
        commentRepository.save(comment)
    }

    fun listComments(postId: Long, page: Int, size: Int): Page<Comment> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"))
        return commentRepository.findPageByPostId(postId, pageable)
    }
}
