package com.sportsapp.domain.post

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

@Service
class PostDomainService(
    private val postRepository: PostRepository,
) {

    fun createPost(
        type: PostType,
        title: String,
        content: String,
        userId: Long,
        writer: String,
    ): Post {
        val post = Post.create(
            type = type,
            title = title,
            content = content,
            userId = userId,
            writer = writer,
            createdAt = ZonedDateTime.now(),
        )
        return postRepository.save(post)
    }

    fun getPost(postId: String): Post =
        postRepository.findById(postId)
            ?: throw ResourceNotFoundException("Post", postId)

    fun updatePost(
        postId: String,
        requestingUserId: Long,
        newTitle: String,
        newContent: String,
    ): Post {
        val post = getPost(postId)
        val updated = post.changeContent(requestingUserId, newTitle, newContent)
        return postRepository.save(updated)
    }
}
