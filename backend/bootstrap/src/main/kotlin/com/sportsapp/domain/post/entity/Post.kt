package com.sportsapp.domain.post.entity

import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.post.exception.NoticeRequiresHostException
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

    @Column(name = "community_id")
    private val communityId: Long?,

    @Column(name = "sport_category", length = 30)
    @Enumerated(EnumType.STRING)
    private val sportCategory: SportCategory?,

    @Column(name = "global_listed", nullable = false)
    private val globalListed: Boolean,
) : JpaAuditingBase() {

    @OneToMany(mappedBy = "post", cascade = [CascadeType.PERSIST, CascadeType.MERGE], fetch = FetchType.LAZY)
    val comments: MutableList<Comment> = mutableListOf()

    val currentCommunityId: Long? get() = communityId
    val currentSportCategory: SportCategory? get() = sportCategory
    val isGlobalListed: Boolean get() = globalListed

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
        /** 전역 게시글 생성 — 모임 미소속, 항상 전역 피드에 노출된다 (FR-4/5 하위 호환). */
        fun create(
            userId: Long,
            title: String,
            content: String,
            type: PostType = PostType.FREE,
            sportCategory: SportCategory? = null,
        ): Post {
            require(title.isNotBlank()) { "title must not be blank" }
            require(title.length <= 200) { "title must not exceed 200 characters" }
            require(content.isNotBlank()) { "content must not be blank" }
            require(content.length <= 10000) { "content must not exceed 10000 characters" }
            return Post(
                userId = userId,
                title = title,
                content = content,
                type = type,
                communityId = null,
                sportCategory = sportCategory,
                globalListed = true,
            )
        }

        /**
         * 모임 소속 게시글 생성 — NOTICE 타입은 방장만 작성 가능하고, sportCategory 는
         * 모임 값을 그대로 상속하며, globalListed 는 모임 공개 여부를 따른다 (FR-7/5, C-1).
         * host·공개 여부는 community 도메인 참조 없이 primitive 로 전달받는다 (R1).
         */
        fun createInCommunity(
            userId: Long,
            title: String,
            content: String,
            type: PostType,
            communityId: Long,
            sportCategory: SportCategory?,
            authorIsHost: Boolean,
            communityIsPublic: Boolean,
        ): Post {
            require(title.isNotBlank()) { "title must not be blank" }
            require(title.length <= 200) { "title must not exceed 200 characters" }
            require(content.isNotBlank()) { "content must not be blank" }
            require(content.length <= 10000) { "content must not exceed 10000 characters" }
            if (type == PostType.NOTICE && !authorIsHost) {
                throw NoticeRequiresHostException(communityId, userId)
            }
            return Post(
                userId = userId,
                title = title,
                content = content,
                type = type,
                communityId = communityId,
                sportCategory = sportCategory,
                globalListed = communityIsPublic,
            )
        }
    }
}
