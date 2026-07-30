package com.sportsapp.presentation.post.controller

import com.sportsapp.application.post.dto.CreateCommunityPostCommand
import com.sportsapp.application.post.dto.CreatePostCommand
import com.sportsapp.application.post.usecase.CreateCommunityPostUseCase
import com.sportsapp.application.post.usecase.CreatePostUseCase
import com.sportsapp.application.post.usecase.GetPostUseCase
import com.sportsapp.application.post.usecase.SearchPostsUseCase
import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.community.exception.NotCommunityMemberException
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.vo.PostType
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import com.sportsapp.presentation.support.fixedPrincipalResolver
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private const val TEST_USER_ID = 100L

/** AUTH-04 — 작성은 `UserPrincipal`(non-null), 상세 조회는 익명 브라우징 허용을 위해 `UserPrincipal?`(nullable)로 식별한다. */
class PostApiControllerTest : BehaviorSpec({

    fun initAuditFields(entity: Any) {
        val superclass = entity.javaClass.superclass
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = superclass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(entity, ZonedDateTime.now())
        }
    }

    fun buildMockMvc(
        searchPostsUseCase: SearchPostsUseCase = mockk(),
        getPostUseCase: GetPostUseCase = mockk(),
        createPostUseCase: CreatePostUseCase = mockk(),
        createCommunityPostUseCase: CreateCommunityPostUseCase = mockk(),
        userId: Long? = TEST_USER_ID,
    ) = MockMvcBuilders.standaloneSetup(
        PostApiController(searchPostsUseCase, getPostUseCase, createPostUseCase, createCommunityPostUseCase),
    )
        .setControllerAdvice(GlobalExceptionHandler())
        .setCustomArgumentResolvers(fixedPrincipalResolver(userId))
        .build()

    Given("communityId 없이 게시글 작성을 요청하면") {
        val createPostUseCase = mockk<CreatePostUseCase>()
        val post = Post.create(userId = TEST_USER_ID, title = "제목", content = "내용").also { initAuditFields(it) }
        every { createPostUseCase.execute(any()) } returns post
        val mockMvc = buildMockMvc(createPostUseCase = createPostUseCase)

        When("POST /posts 요청 시") {
            val body = """{"title":"제목","content":"내용"}"""
            val result = mockMvc.perform(post("/posts").contentType(MediaType.APPLICATION_JSON).content(body))

            Then("전역 게시글로 CreatePostUseCase 가 호출되고 201을 반환한다") {
                result.andExpect(status().isCreated)
                    .andExpect(jsonPath("$.userId").value(TEST_USER_ID))
                verify(exactly = 1) { createPostUseCase.execute(any<CreatePostCommand>()) }
            }
        }
    }

    Given("communityId 를 포함해 게시글 작성을 요청하면") {
        val createCommunityPostUseCase = mockk<CreateCommunityPostUseCase>()
        val post = Post.createInCommunity(
            userId = TEST_USER_ID,
            title = "공지",
            content = "내용",
            type = PostType.NOTICE,
            communityId = 10L,
            sportCategory = SportCategory.SOCCER,
            authorIsHost = true,
            communityIsPublic = true,
        ).also { initAuditFields(it) }
        every { createCommunityPostUseCase.execute(any()) } returns post
        val mockMvc = buildMockMvc(createCommunityPostUseCase = createCommunityPostUseCase)

        When("POST /posts 요청 시") {
            val body = """{"title":"공지","content":"내용","type":"NOTICE","communityId":10}"""
            val result = mockMvc.perform(post("/posts").contentType(MediaType.APPLICATION_JSON).content(body))

            Then("CreateCommunityPostUseCase 가 호출되고 communityId 가 응답에 포함된다") {
                result.andExpect(status().isCreated)
                    .andExpect(jsonPath("$.communityId").value(10))
                verify(exactly = 1) { createCommunityPostUseCase.execute(any<CreateCommunityPostCommand>()) }
            }
        }
    }

    Given("PRIVATE 모임에 비멤버가 게시글 작성을 요청하면") {
        val createCommunityPostUseCase = mockk<CreateCommunityPostUseCase>()
        every { createCommunityPostUseCase.execute(any()) } throws NotCommunityMemberException(10L, TEST_USER_ID)
        val mockMvc = buildMockMvc(createCommunityPostUseCase = createCommunityPostUseCase)

        When("POST /posts 요청 시") {
            val body = """{"title":"제목","content":"내용","communityId":10}"""
            val result = mockMvc.perform(post("/posts").contentType(MediaType.APPLICATION_JSON).content(body))

            Then("403을 반환한다") {
                result.andExpect(status().isForbidden)
            }
        }
    }

    Given("인증 없이 게시글 상세를 조회하면") {
        val getPostUseCase = mockk<GetPostUseCase>()
        val post = Post.create(userId = TEST_USER_ID, title = "제목", content = "내용").also { initAuditFields(it) }
        every { getPostUseCase.execute(postId = 1L, requesterId = null) } returns Pair(post, emptyList())
        val mockMvc = buildMockMvc(getPostUseCase = getPostUseCase, userId = null)

        When("GET /posts/1 요청 시") {
            val result = mockMvc.perform(get("/posts/1"))

            Then("200과 함께 상세를 반환한다") {
                result.andExpect(status().isOk)
                verify(exactly = 1) { getPostUseCase.execute(postId = 1L, requesterId = null) }
            }
        }
    }

    Given("인증된 사용자가 PRIVATE 모임 게시글 상세를 비멤버로 조회하면") {
        val getPostUseCase = mockk<GetPostUseCase>()
        every { getPostUseCase.execute(postId = 2L, requesterId = TEST_USER_ID) } throws NotCommunityMemberException(20L, TEST_USER_ID)
        val mockMvc = buildMockMvc(getPostUseCase = getPostUseCase)

        When("GET /posts/2 요청 시") {
            val result = mockMvc.perform(get("/posts/2"))

            Then("403을 반환한다") {
                result.andExpect(status().isForbidden)
            }
        }
    }

    Given("communityId·sportCategory 쿼리로 전역 피드를 검색하면") {
        val searchPostsUseCase = mockk<SearchPostsUseCase>()
        every { searchPostsUseCase.execute(any()) } returns PageImpl(emptyList(), PageRequest.of(0, 20), 0)
        val mockMvc = buildMockMvc(searchPostsUseCase = searchPostsUseCase)

        When("GET /posts?communityId=10&sportCategory=SOCCER 요청 시") {
            val result = mockMvc.perform(get("/posts").param("communityId", "10").param("sportCategory", "SOCCER"))

            Then("200을 반환한다") {
                result.andExpect(status().isOk)
                verify(exactly = 1) { searchPostsUseCase.execute(match { it.communityId == 10L && it.sportCategory == SportCategory.SOCCER }) }
            }
        }
    }
})
