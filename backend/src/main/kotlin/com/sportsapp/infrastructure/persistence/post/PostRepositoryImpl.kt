package com.sportsapp.infrastructure.persistence.post

import com.sportsapp.domain.post.Post
import com.sportsapp.domain.post.PostRepository
import com.sportsapp.domain.post.PostType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class PostRepositoryImpl(
    private val postMongoRepository: PostMongoRepository,
) : PostRepository {

    override fun save(post: Post): Post =
        postMongoRepository.save(PostDocument.fromDomain(post)).toDomain()

    override fun findById(id: String): Post? =
        postMongoRepository.findById(id).orElse(null)?.toDomain()

    override fun findByUserId(userId: Long, pageable: Pageable): Page<Post> =
        postMongoRepository.findByUserId(userId, pageable).map { it.toDomain() }

    override fun findByType(type: PostType, pageable: Pageable): Page<Post> =
        postMongoRepository.findByType(type.name, pageable).map { it.toDomain() }

    override fun delete(id: String) =
        postMongoRepository.deleteById(id)
}
