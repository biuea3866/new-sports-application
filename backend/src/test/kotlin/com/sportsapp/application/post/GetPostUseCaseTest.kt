package com.sportsapp.application.post

import com.sportsapp.application.post.usecase.GetPostUseCase

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.post.entity.Comment
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.service.PostDomainService
import com.sportsapp.domain.post.vo.PostType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class GetPostUseCaseTest : BehaviorSpec({

    val postDomainService = mockk<PostDomainService>()
    val getPostUseCase = GetPostUseCase(postDomainService)

    fun initAuditFields(entity: Any) {
        val superclass = entity.javaClass.superclass
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = superclass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(entity, ZonedDateTime.now())
        }
    }

    Given("[U-02] 존재하지 않는 postId로 조회하면") {
        every { postDomainService.getDetail(99999L) } throws ResourceNotFoundException("Post", 99999L)

        When("execute를 호출하면") {
            Then("[U-02] PostNotFoundException(ResourceNotFoundException)을 던진다") {
                shouldThrow<ResourceNotFoundException> {
                    getPostUseCase.execute(99999L)
                }
            }
        }
    }

    Given("[U-03] 댓글 3건이 있는 Post를 조회하면") {
        val post = Post.create(userId = 1L, title = "제목", content = "내용", type = PostType.FREE)
            .also { initAuditFields(it) }
        val comments = (1..3).map {
            Comment.create(post = post, userId = it.toLong(), content = "댓글 $it")
                .also { comment -> initAuditFields(comment) }
        }
        every { postDomainService.getDetail(1L) } returns Pair(post, comments)

        When("execute를 호출하면") {
            val result = getPostUseCase.execute(1L)

            Then("[U-03] 댓글 3건이 포함된 Pair가 반환된다") {
                result.second.size shouldBe 3
            }
        }
    }
})
