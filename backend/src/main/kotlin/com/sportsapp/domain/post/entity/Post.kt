package com.sportsapp.domain.post.entity

import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.post.vo.PostType
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "posts")
class Post private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "title", nullable = false, length = 200)
    var title: String,

    @Column(name = "content", nullable = false, length = 10000)
    var content: String,

    @Column(name = "type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    val type: PostType,
) : JpaAuditingBase() {

    @OneToMany(mappedBy = "post", cascade = [CascadeType.PERSIST, CascadeType.MERGE], fetch = FetchType.LAZY)
    val comments: MutableList<Comment> = mutableListOf()

    fun addComment(userId: Long, content: String): Comment {
        val comment = Comment.create(post = this, userId = userId, content = content)
        comments.add(comment)
        return comment
    }

    fun changePost(title: String, content: String) {
        require(title.isNotBlank()) { "title must not be blank" }
        require(title.length <= 200) { "title must not exceed 200 characters" }
        require(content.isNotBlank()) { "content must not be blank" }
        require(content.length <= 10000) { "content must not exceed 10000 characters" }
        this.title = title
        this.content = content
    }

    companion object {
        fun create(userId: Long, title: String, content: String, type: PostType = PostType.FREE): Post {
            require(title.isNotBlank()) { "title must not be blank" }
            require(title.length <= 200) { "title must not exceed 200 characters" }
            require(content.isNotBlank()) { "content must not be blank" }
            require(content.length <= 10000) { "content must not exceed 10000 characters" }
            return Post(
                userId = userId,
                title = title,
                content = content,
                type = type,
            )
        }
    }
}
