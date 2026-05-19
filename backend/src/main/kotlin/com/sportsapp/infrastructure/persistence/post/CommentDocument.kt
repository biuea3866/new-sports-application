package com.sportsapp.infrastructure.persistence.post

import com.sportsapp.domain.post.Comment
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.ZonedDateTime

@Document(collection = "comments")
@CompoundIndex(name = "idx_post_id_created_at", def = "{'postId': 1, 'createdAt': 1}")
data class CommentDocument(
    @Id
    val id: String?,
    @Indexed
    val postId: String,
    val content: String,
    @Indexed
    val userId: Long,
    val writer: String,
    val createdAt: ZonedDateTime,
) {

    fun toDomain(): Comment = Comment.reconstitute(
        id = requireNotNull(id) { "CommentDocument.id must not be null" },
        postId = postId,
        content = content,
        userId = userId,
        writer = writer,
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(comment: Comment): CommentDocument = CommentDocument(
            id = comment.id,
            postId = comment.postId,
            content = comment.content,
            userId = comment.userId,
            writer = comment.writer,
            createdAt = comment.createdAt,
        )
    }
}
