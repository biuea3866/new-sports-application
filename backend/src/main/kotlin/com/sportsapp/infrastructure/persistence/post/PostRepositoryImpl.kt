package com.sportsapp.infrastructure.persistence.post

import com.sportsapp.domain.post.Post
import com.sportsapp.domain.post.PostRepository
import org.springframework.stereotype.Repository

@Repository
class PostRepositoryImpl(
    private val postJpaRepository: PostJpaRepository,
) : PostRepository {

    override fun save(post: Post): Post = postJpaRepository.save(post)

    override fun findById(id: Long): Post? = postJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun findByUserId(userId: Long): List<Post> =
        postJpaRepository.findByUserIdAndDeletedAtIsNull(userId)
}
