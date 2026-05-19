package com.sportsapp.infrastructure.persistence.post

import com.sportsapp.domain.post.Post
import com.sportsapp.domain.post.PostType
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.IndexDirection
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.index.TextIndexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.ZonedDateTime

@Document(collection = "posts")
@CompoundIndex(name = "idx_type_created_at", def = "{'type': 1, 'createdAt': -1}")
data class PostDocument(
    @Id
    val id: String?,
    @Indexed
    val type: String,
    @TextIndexed
    val title: String,
    @TextIndexed
    val content: String,
    @Indexed
    val userId: Long,
    val writer: String,
    @Indexed(direction = IndexDirection.DESCENDING)
    val createdAt: ZonedDateTime,
) {

    fun toDomain(): Post = Post.reconstitute(
        id = requireNotNull(id) { "PostDocument.id must not be null" },
        type = PostType.valueOf(type),
        title = title,
        content = content,
        userId = userId,
        writer = writer,
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(post: Post): PostDocument = PostDocument(
            id = post.id,
            type = post.type.name,
            title = post.title,
            content = post.content,
            userId = post.userId,
            writer = post.writer,
            createdAt = post.createdAt,
        )
    }
}
