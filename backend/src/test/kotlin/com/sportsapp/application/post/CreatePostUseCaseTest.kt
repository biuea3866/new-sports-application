package com.sportsapp.application.post

import com.sportsapp.application.post.dto.CreatePostCommand
import com.sportsapp.application.post.usecase.CreatePostUseCase

import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.service.PostDomainService
import com.sportsapp.domain.post.vo.PostType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.ZonedDateTime

class CreatePostUseCaseTest : BehaviorSpec({

    val postDomainService = mockk<PostDomainService>()
    val createPostUseCase = CreatePostUseCase(postDomainService)

    fun buildSavedPost(userId: Long, title: String, content: String): Post {
        val post = Post.create(userId = userId, title = title, content = content)
        val createdAtField = post.javaClass.superclass.getDeclaredField("createdAt")
        createdAtField.isAccessible = true
        createdAtField.set(post, ZonedDateTime.now())
        val updatedAtField = post.javaClass.superclass.getDeclaredField("updatedAt")
        updatedAtField.isAccessible = true
        updatedAtField.set(post, ZonedDateTime.now())
        return post
    }

    Given("[U-01] 유효한 제목과 본문으로 CreatePostCommand를 전달할 때") {
        val commandSlot = slot<String>()
        every {
            postDomainService.createPost(
                userId = 1L,
                title = capture(commandSlot),
                content = any(),
            )
        } returns buildSavedPost(userId = 1L, title = "제목", content = "본문 내용")

        When("execute를 호출하면") {
            val command = CreatePostCommand(userId = 1L, title = "제목", content = "본문 내용")
            val result = createPostUseCase.execute(command)

            Then("[U-01] PostResponse가 반환된다") {
                result.userId shouldBe 1L
                result.title shouldBe "제목"
                result.type shouldBe PostType.FREE
            }
        }
    }

    Given("[U-02] DomainService에 전달되는 파라미터 검증") {
        val userIdSlot = slot<Long>()
        val titleSlot = slot<String>()
        val contentSlot = slot<String>()
        every {
            postDomainService.createPost(
                userId = capture(userIdSlot),
                title = capture(titleSlot),
                content = capture(contentSlot),
            )
        } returns buildSavedPost(userId = 42L, title = "테스트 제목", content = "테스트 본문")

        When("execute를 호출하면") {
            val command = CreatePostCommand(userId = 42L, title = "테스트 제목", content = "테스트 본문")
            createPostUseCase.execute(command)

            Then("[U-02] Command의 userId/title/content가 DomainService에 그대로 전달된다") {
                userIdSlot.captured shouldBe 42L
                titleSlot.captured shouldBe "테스트 제목"
                contentSlot.captured shouldBe "테스트 본문"
            }
        }
    }

    Given("type·sportCategory 를 지정한 CreatePostCommand 를 전달할 때") {
        val typeSlot = slot<PostType>()
        val sportCategorySlot = slot<SportCategory>()
        every {
            postDomainService.createPost(
                userId = 1L,
                title = "제목",
                content = "본문",
                type = capture(typeSlot),
                sportCategory = capture(sportCategorySlot),
            )
        } returns buildSavedPost(userId = 1L, title = "제목", content = "본문")

        When("execute를 호출하면") {
            val command = CreatePostCommand(
                userId = 1L,
                title = "제목",
                content = "본문",
                type = PostType.QUESTION,
                sportCategory = SportCategory.SOCCER,
            )
            createPostUseCase.execute(command)

            Then("type·sportCategory 가 DomainService에 그대로 전달된다") {
                typeSlot.captured shouldBe PostType.QUESTION
                sportCategorySlot.captured shouldBe SportCategory.SOCCER
            }
        }
    }
})
