package com.sportsapp.application.post

import com.sportsapp.domain.post.entity.Comment
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.vo.PostType
import com.sportsapp.presentation.post.dto.response.CommentResponse
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime

class CommentResponseTest : BehaviorSpec({

    fun initAuditFields(entity: Any) {
        val superclass = entity.javaClass.superclass
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = superclass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(entity, ZonedDateTime.now())
        }
    }

    Given("저장된 적 없는 Post 와 Comment 가 순수 객체로 존재할 때") {
        val post = Post.create(userId = 1L, title = "제목", content = "내용", type = PostType.FREE)
            .also { initAuditFields(it) }
        val comment = Comment.create(post = post, userId = 2L, content = "댓글")
            .also { initAuditFields(it) }

        When("CommentResponse.of 로 DTO 를 생성하면") {
            val response = CommentResponse.of(comment)

            Then("JPA lazy 프록시 접근 없이 postId 스칼라 값을 읽어 DTO 에 올바르게 매핑한다") {
                response.postId shouldBe post.id
                response.userId shouldBe 2L
                response.content shouldBe "댓글"
            }
        }
    }
})
