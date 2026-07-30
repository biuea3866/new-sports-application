package com.sportsapp.presentation.post.dto.response

import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.vo.PostType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime

class PostDetailResponseTest : BehaviorSpec({

    fun initAuditFields(entity: Any) {
        val superclass = entity.javaClass.superclass
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = superclass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(entity, ZonedDateTime.now())
        }
    }

    Given("전역 게시글을 PostDetailResponse.of 로 변환하면") {
        val post = Post.create(userId = 1L, title = "제목", content = "내용").also { initAuditFields(it) }

        When("변환하면") {
            val response = PostDetailResponse.of(post, emptyList())

            Then("communityId·sportCategory 가 null 로 매핑된다") {
                response.communityId shouldBe null
                response.sportCategory shouldBe null
            }
        }
    }

    Given("모임 소속 게시글을 PostDetailResponse.of 로 변환하면") {
        val post = Post.createInCommunity(
            userId = 1L,
            title = "제목",
            content = "내용",
            type = PostType.FREE,
            communityId = 10L,
            sportCategory = SportCategory.SOCCER,
            authorIsHost = true,
            communityIsPublic = true,
        ).also { initAuditFields(it) }

        When("변환하면") {
            val response = PostDetailResponse.of(post, emptyList())

            Then("communityId·sportCategory 가 게시글 값 그대로 매핑된다") {
                response.communityId shouldBe 10L
                response.sportCategory shouldBe SportCategory.SOCCER
            }
        }
    }
})
