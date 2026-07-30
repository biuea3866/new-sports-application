package com.sportsapp.domain.post.entity

import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.post.exception.NotCommentOwnerException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "comments")
class Comment private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    val post: Post,

    @Column(name = "post_id", insertable = false, updatable = false)
    val postId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "content", nullable = false, length = 2000)
    var content: String,
) : JpaAuditingBase() {

    fun changeContent(content: String) {
        require(content.isNotBlank()) { "content must not be blank" }
        require(content.length <= 2000) { "content must not exceed 2000 characters" }
        this.content = content
    }

    fun delete(requestUserId: Long) {
        if (userId != requestUserId) throw NotCommentOwnerException(id)
        softDelete(requestUserId)
    }

    companion object {
        fun create(post: Post, userId: Long, content: String): Comment {
            require(content.isNotBlank()) { "content must not be blank" }
            require(content.length <= 2000) { "content must not exceed 2000 characters" }
            return Comment(
                post = post,
                postId = post.id,
                userId = userId,
                content = content,
            )
        }
    }
}
