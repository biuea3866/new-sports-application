package com.sportsapp.application.post

import com.sportsapp.application.post.dto.PostCriteria
import com.sportsapp.application.post.usecase.SearchPostsUseCase

import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.service.PostDomainService
import com.sportsapp.domain.post.dto.PostSearchCriteria
import com.sportsapp.domain.post.vo.PostType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.ZonedDateTime

class SearchPostsUseCaseTest : BehaviorSpec({

    val postDomainService = mockk<PostDomainService>()
    val searchPostsUseCase = SearchPostsUseCase(postDomainService)

    fun buildPost(userId: Long = 1L, type: PostType = PostType.FREE): Post {
        val post = Post.create(userId = userId, title = "제목", content = "내용", type = type)
        val createdAtField = post.javaClass.superclass.getDeclaredField("createdAt")
        createdAtField.isAccessible = true
        createdAtField.set(post, ZonedDateTime.now())
        val updatedAtField = post.javaClass.superclass.getDeclaredField("updatedAt")
        updatedAtField.isAccessible = true
        updatedAtField.set(post, ZonedDateTime.now())
        return post
    }

    Given("type/userId/keyword 복합 Criteria가 주어졌을 때") {
        val criteriaSlot = slot<PostSearchCriteria>()
        every { postDomainService.search(capture(criteriaSlot), any()) } returns PageImpl(emptyList())

        When("execute를 호출하면") {
            val criteria = PostCriteria(
                type = PostType.FREE,
                userId = 7L,
                keyword = "풋살",
                page = 0,
                size = 10,
            )
            searchPostsUseCase.execute(criteria)

            Then("Criteria의 type/userId/keyword가 PostSearchCriteria로 변환되어 DomainService에 전달된다") {
                criteriaSlot.captured.type shouldBe PostType.FREE
                criteriaSlot.captured.userId shouldBe 7L
                criteriaSlot.captured.keyword shouldBe "풋살"
            }
        }
    }

    Given("빈 keyword가 주어졌을 때") {
        val criteriaSlot = slot<PostSearchCriteria>()
        every { postDomainService.search(capture(criteriaSlot), any()) } returns PageImpl(emptyList())

        When("execute를 호출하면") {
            val criteria = PostCriteria(type = null, userId = null, keyword = "  ", page = 0, size = 10)
            searchPostsUseCase.execute(criteria)

            Then("keyword가 null로 변환되어 전달된다") {
                criteriaSlot.captured.keyword shouldBe null
            }
        }
    }

    Given("FREE 타입 Post 3건이 존재할 때") {
        val posts = (1..3).map { buildPost(userId = it.toLong(), type = PostType.FREE) }
        every { postDomainService.search(any(), any()) } returns PageImpl(posts)

        When("type=FREE로 조회하면") {
            val criteria = PostCriteria(type = PostType.FREE, userId = null, keyword = null, page = 0, size = 20)
            val result = searchPostsUseCase.execute(criteria)

            Then("3건의 PostResponse가 반환된다") {
                result.totalElements shouldBe 3
                result.content.all { it.type == PostType.FREE } shouldBe true
            }
        }
    }

    Given("size=200으로 요청했을 때") {
        val pageableSlot = slot<Pageable>()
        every { postDomainService.search(any(), capture(pageableSlot)) } returns PageImpl(emptyList())

        When("execute를 호출하면") {
            val criteria = PostCriteria(type = null, userId = null, keyword = null, page = 0, size = 200)
            searchPostsUseCase.execute(criteria)

            Then("Pageable의 size가 100으로 cap된다") {
                pageableSlot.captured.pageSize shouldBe 100
            }
        }
    }

    Given("globalFeedOnly=false 로 요청해도") {
        val criteriaSlot = slot<PostSearchCriteria>()
        every { postDomainService.search(capture(criteriaSlot), any()) } returns PageImpl(emptyList())

        When("execute를 호출하면") {
            val criteria = PostCriteria(
                type = null,
                userId = null,
                keyword = null,
                globalFeedOnly = false,
                page = 0,
                size = 10,
            )
            searchPostsUseCase.execute(criteria)

            Then("전역 피드이므로 globalFeedOnly 가 true 로 강제된다") {
                criteriaSlot.captured.globalFeedOnly shouldBe true
            }
        }
    }
})
