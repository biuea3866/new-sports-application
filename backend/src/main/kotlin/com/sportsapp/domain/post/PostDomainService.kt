package com.sportsapp.domain.post

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

@Service
class PostDomainService(
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val customPostRepository: CustomPostRepository,
) {
    fun createPost(userId: Long, title: String, content: String): Post {
        val post = Post.create(userId = userId, title = title, content = content)
        return postRepository.save(post)
    }

    fun changePost(postId: Long, title: String, content: String): Post {
        val post = postRepository.findById(postId)
            ?: throw ResourceNotFoundException("Post", postId)
        post.changePost(title = title, content = content)
        return postRepository.save(post)
    }

    fun deletePost(postId: Long, userId: Long): Post {
        val post = postRepository.findById(postId)
            ?: throw ResourceNotFoundException("Post", postId)
        post.softDelete(userId)
        return postRepository.save(post)
    }

    fun getPost(postId: Long): Post =
        postRepository.findById(postId)
            ?: throw ResourceNotFoundException("Post", postId)

    fun search(criteria: PostSearchCriteria, pageable: Pageable): Page<Post> =
        customPostRepository.findByCriteria(criteria, pageable)

    fun getDetail(postId: Long): Pair<Post, List<Comment>> {
        val post = postRepository.findById(postId)
            ?: throw ResourceNotFoundException("Post", postId)
        val comments = commentRepository.findTop50ByPostId(postId)
        return Pair(post, comments)
    }

    fun addComment(postId: Long, userId: Long, content: String): Comment {
        postRepository.findById(postId)
            ?: throw ResourceNotFoundException("Post", postId)
        val comment = Comment.create(postId = postId, userId = userId, content = content)
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
