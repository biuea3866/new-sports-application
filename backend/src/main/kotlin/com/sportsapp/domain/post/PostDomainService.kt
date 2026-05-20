package com.sportsapp.domain.post

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import org.springframework.stereotype.Service

@Service
class PostDomainService(
    private val postRepository: PostRepository,
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
}
