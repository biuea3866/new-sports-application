package com.sportsapp.domain.post

import com.sportsapp.domain.common.JpaAuditingBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "comments")
class Comment private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L,

    @Column(name = "post_id", nullable = false)
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

    companion object {
        fun create(postId: Long, userId: Long, content: String): Comment {
            require(content.isNotBlank()) { "content must not be blank" }
            require(content.length <= 2000) { "content must not exceed 2000 characters" }
            return Comment(
                postId = postId,
                userId = userId,
                content = content,
            )
        }
    }
}
